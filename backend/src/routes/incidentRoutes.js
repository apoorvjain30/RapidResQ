import express from 'express';
import multer from 'multer';
import crypto from 'crypto';
import rateLimit from 'express-rate-limit';
import { supabase } from '../config/supabase.js';
import { logAudit, formatIstTime } from '../services/audit.js';
import { analyzeIncidentTriage } from '../services/geminiService.js';
import { requireAuth } from '../middleware/auth.js';
import { getAutoDispatchEnabled } from './adminRoutes.js';

// In-memory fallback stores when Supabase is not connected
export const inMemoryIncidents = new Map();
export const inMemoryTimeline = new Map(); // incidentId -> array of entries
export const inMemoryNotes = new Map(); // incidentId -> array of notes
export const inMemoryTeams = new Map([
  ['TEAM-ALPHA', { id: 'TEAM-ALPHA', name: 'Unit Alpha', unitType: 'Medical EMS', baseLocation: 'Indore Hospital', lat: 22.7196, lng: 75.8577, isAvailable: true, resolvedCount: 12 }],
  ['TEAM-BRAVO', { id: 'TEAM-BRAVO', name: 'Unit Bravo', unitType: 'Fire & Hazmat', baseLocation: 'Vijay Nagar Fire Station', lat: 22.7533, lng: 75.8937, isAvailable: false, resolvedCount: 45 }],
  ['TEAM-CHARLIE', { id: 'TEAM-CHARLIE', name: 'Unit Charlie', unitType: 'Traffic & Rescue', baseLocation: 'Bhawarkua Square', lat: 22.6953, lng: 75.8679, isAvailable: true, resolvedCount: 30 }],
  ['TEAM-DELTA', { id: 'TEAM-DELTA', name: 'Unit Delta', unitType: 'Security Response', baseLocation: 'Palasia Police Station', lat: 22.7231, lng: 75.8920, isAvailable: true, resolvedCount: 18 }]
]);

// Seed initial realistic Indore demo incidents in memory
(function seedDemoIncidents() {
  const now = Date.now();
  const hour = 3600000;

  const inc1 = {
    id: 'RRQ-K8F2P9',
    category: 'FIRE_HAZARD',
    title: 'Vijay Nagar Commercial Fire',
    description: 'Heavy smoke spotted on 3rd floor of commercial complex',
    locationText: 'Vijay Nagar Square, near Mall',
    gpsLabel: '22.7533, 75.8937',
    lat: 22.7533,
    lng: 75.8937,
    severity: 'CRITICAL',
    reporterContact: '9876543210',
    status: 'EN_ROUTE',
    assignedTeamId: 'TEAM-BRAVO',
    assignedTeamName: 'Unit Bravo',
    createdAt: now - 2 * hour,
    aiSummary: 'Heavy smoke on 3rd floor of commercial complex in Vijay Nagar. Fire unit dispatched.',
    aiSuggestedSeverity: 'CRITICAL',
    aiRecommendedUnit: 'Fire & Hazmat',
    aiSpamScore: 2
  };

  const inc2 = {
    id: 'RRQ-M4L9X1',
    category: 'ROAD_ACCIDENT',
    title: 'Multiple Vehicle Collision',
    description: 'Two cars and a bike involved, traffic blocked',
    locationText: 'Bhawarkua Road',
    gpsLabel: '22.6953, 75.8679',
    lat: 22.6953,
    lng: 75.8679,
    severity: 'HIGH',
    reporterContact: '9876543211',
    status: 'VERIFIED',
    assignedTeamId: null,
    assignedTeamName: null,
    createdAt: now - hour,
    aiSummary: 'Two cars and a bike involved on Bhawarkua Road. Traffic blocked, rescue required.',
    aiSuggestedSeverity: 'HIGH',
    aiRecommendedUnit: 'Traffic & Rescue',
    aiSpamScore: 4
  };

  const inc3 = {
    id: 'RRQ-H3J7Q5',
    category: 'MEDICAL',
    title: 'Heart Attack Suspected',
    description: 'Elderly male unconscious, breathing shallow',
    locationText: 'Palasia',
    gpsLabel: '22.7231, 75.8920',
    lat: 22.7231,
    lng: 75.8920,
    severity: 'CRITICAL',
    reporterContact: '9876543212',
    status: 'RESOLVED',
    assignedTeamId: 'TEAM-ALPHA',
    assignedTeamName: 'Unit Alpha',
    createdAt: now - 5 * hour,
    aiSummary: 'Elderly male unconscious with shallow breathing in Palasia. EMS dispatched and successfully resolved.',
    aiSuggestedSeverity: 'CRITICAL',
    aiRecommendedUnit: 'Medical EMS',
    aiSpamScore: 1
  };

  [inc1, inc2, inc3].forEach(inc => inMemoryIncidents.set(inc.id, inc));

  inMemoryTimeline.set('RRQ-K8F2P9', [
    { timestamp: now - 2 * hour, statusLabel: 'Reported', actor: 'Citizen' },
    { timestamp: now - 1.9 * hour, statusLabel: 'Verified', actor: 'Dispatcher' },
    { timestamp: now - 1.8 * hour, statusLabel: 'Assigned to Unit Bravo', actor: 'Dispatcher' },
    { timestamp: now - 1.7 * hour, statusLabel: 'Status updated to ACCEPTED', actor: 'Unit Bravo' },
    { timestamp: now - 1.6 * hour, statusLabel: 'Status updated to EN_ROUTE', actor: 'Unit Bravo' }
  ]);

  inMemoryTimeline.set('RRQ-M4L9X1', [
    { timestamp: now - hour, statusLabel: 'Reported', actor: 'Citizen' },
    { timestamp: now - 0.5 * hour, statusLabel: 'Verified', actor: 'Dispatcher' }
  ]);

  inMemoryTimeline.set('RRQ-H3J7Q5', [
    { timestamp: now - 5 * hour, statusLabel: 'Reported', actor: 'Citizen' },
    { timestamp: now - 4.9 * hour, statusLabel: 'Verified', actor: 'Dispatcher' },
    { timestamp: now - 4.8 * hour, statusLabel: 'Assigned to Unit Alpha', actor: 'Dispatcher' },
    { timestamp: now - 4.7 * hour, statusLabel: 'Status updated to ACCEPTED', actor: 'Unit Alpha' },
    { timestamp: now - 4.5 * hour, statusLabel: 'Status updated to EN_ROUTE', actor: 'Unit Alpha' },
    { timestamp: now - 4.2 * hour, statusLabel: 'Status updated to ON_SITE', actor: 'Unit Alpha' },
    { timestamp: now - 3 * hour, statusLabel: 'Status updated to RESPONDING', actor: 'Unit Alpha' },
    { timestamp: now - 2 * hour, statusLabel: 'Status updated to RESOLVED', actor: 'Unit Alpha' }
  ]);
})();

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 15 * 1024 * 1024 }, // 15 MB
  fileFilter: (req, file, cb) => {
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/quicktime'];
    if (allowedTypes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('Invalid file type. Allowed: JPEG, PNG, WEBP, MP4, MOV.'));
    }
  }
});

const reportRateLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 min
  max: 10, // 10 reports per 15 min
  message: { error: 'Rate Limit Exceeded', message: 'Too many reports submitted. Please wait a few minutes.' }
});

function generateIncidentId() {
  const allowedChars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let chars = '';
  for (let i = 0; i < 6; i++) {
    chars += allowedChars[crypto.randomInt(0, allowedChars.length)];
  }
  return `RRQ-${chars}`;
}

export function hydrateIncident(inc) {
  const timeline = inMemoryTimeline.get(inc.id) || [];
  const fieldNotes = inMemoryNotes.get(inc.id) || [];
  const team = inc.assignedTeamId ? inMemoryTeams.get(inc.assignedTeamId) : null;

  return {
    id: inc.id,
    category: inc.category,
    title: inc.title,
    description: inc.description,
    locationText: inc.locationText,
    gpsLabel: inc.gpsLabel,
    lat: inc.lat,
    lng: inc.lng,
    severity: inc.severity,
    reporterContact: inc.reporterContact,
    media: inc.media || null,
    status: inc.status,
    assignedTeamId: inc.assignedTeamId,
    assignedTeamName: inc.assignedTeamName,
    assignedTeamLat: team ? team.lat : null,
    assignedTeamLng: team ? team.lng : null,
    rejectionReason: inc.rejectionReason || null,
    aiSummary: inc.aiSummary || null,
    aiSuggestedSeverity: inc.aiSuggestedSeverity || null,
    aiRecommendedUnit: inc.aiRecommendedUnit || null,
    aiSpamScore: inc.aiSpamScore !== undefined ? inc.aiSpamScore : null,
    createdAt: inc.createdAt,
    timeline,
    fieldNotes
  };
}

export function createIncidentRoutes(io) {
  const router = express.Router();

  // Helper to emit real-time updates
  async function broadcastUpdates(incidentId) {
    const inc = inMemoryIncidents.get(incidentId);
    if (!inc) return;

    const hydrated = hydrateIncident(inc);

    // Broadcast to staff room
    io.to('staff').emit('incidents:updated', hydrated);

    // Broadcast to tracking room (sanitized - no reporter contact)
    const sanitized = { ...hydrated };
    delete sanitized.reporterContact;
    io.to(`track:${incidentId}`).emit('incident:updated', sanitized);
  }

  // 1. POST /api/incidents (PUBLIC)
  router.post('/', reportRateLimiter, upload.single('media'), async (req, res) => {
    try {
      const {
        category,
        title,
        description,
        locationText,
        gpsLabel,
        lat,
        lng,
        severity,
        reporterContact
      } = req.body;

      if (!category || !locationText) {
        return res.status(400).json({ error: 'Validation Error', message: 'Category and locationText are required' });
      }

      const id = generateIncidentId();
      const now = Date.now();
      let mediaAttachments = null;

      // Handle media file upload to Supabase Storage if present
      if (req.file) {
        const isVideo = req.file.mimetype.startsWith('video');
        const fileExt = req.file.originalname.split('.').pop() || (isVideo ? 'mp4' : 'jpg');
        const fileName = `${id}-${now}.${fileExt}`;

        let mediaUri = `demo://media/${fileName}`;

        if (supabase) {
          try {
            const { data, error } = await supabase.storage
              .from('incident-media')
              .upload(fileName, req.file.buffer, {
                contentType: req.file.mimetype,
                upsert: true
              });

            if (!error) {
              const { data: publicUrlData } = supabase.storage
                .from('incident-media')
                .getPublicUrl(fileName);

              if (publicUrlData?.publicUrl) {
                mediaUri = publicUrlData.publicUrl;
              }
            } else {
              console.error('[STORAGE UPLOAD ERROR]', error.message);
            }
          } catch (sErr) {
            console.error('[STORAGE ERROR]', sErr.message);
          }
        }

        mediaAttachments = [{ uri: mediaUri, isVideo }];
      }

      let possibleDuplicateOf = null;
      const parsedLat = parseFloat(lat);
      const parsedLng = parseFloat(lng);

      if (!isNaN(parsedLat) && !isNaN(parsedLng) && supabase) {
        try {
          const { data: dups, error } = await supabase.rpc('find_nearby_duplicates', {
            p_lat: parsedLat,
            p_lng: parsedLng,
            p_category: category,
            p_radius_m: 500,
            p_minutes: 120
          });

          if (!error && dups && dups.length > 0) {
            possibleDuplicateOf = dups[0].id;
          }
        } catch (dErr) {
          console.error('[POSTGIS DUPLICATE CHECK ERROR]', dErr.message);
        }
      }

      const newIncident = {
        id,
        category,
        title: title || `${category} Emergency`,
        description: description || '',
        locationText,
        gpsLabel: gpsLabel || (parsedLat && parsedLng ? `${parsedLat}° N, ${parsedLng}° E` : null),
        lat: isNaN(parsedLat) ? null : parsedLat,
        lng: isNaN(parsedLng) ? null : parsedLng,
        severity: severity || 'HIGH',
        reporterContact: reporterContact || '',
        media: mediaAttachments,
        status: 'REPORTED',
        assignedTeamId: null,
        assignedTeamName: null,
        createdAt: now,
        possibleDuplicateOf
      };

      inMemoryIncidents.set(id, newIncident);
      inMemoryTimeline.set(id, [{ timestamp: now, statusLabel: 'Reported', actor: 'Citizen' }]);
      inMemoryNotes.set(id, []);

      if (supabase) {
        try {
          await supabase.from('incidents').insert([{
            id: newIncident.id,
            category: newIncident.category,
            title: newIncident.title,
            description: newIncident.description,
            location_text: newIncident.locationText,
            gps_label: newIncident.gpsLabel,
            lat: newIncident.lat,
            lng: newIncident.lng,
            severity: newIncident.severity,
            reporter_contact: newIncident.reporterContact,
            media_url: mediaAttachments ? mediaAttachments[0]?.uri : null,
            media_is_video: mediaAttachments ? Boolean(mediaAttachments[0]?.isVideo) : false,
            status: newIncident.status,
            created_at: new Date(now).toISOString(),
            possible_duplicate_of: possibleDuplicateOf,
            assigned_team_id: newIncident.assignedTeamId,
            assigned_team_name: newIncident.assignedTeamName
          }]);

          await supabase.from('incident_timeline').insert(inMemoryTimeline.get(id).map((entry, index) => ({
            id: `TL-${now}-${index}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(entry.timestamp)),
            status_label: entry.statusLabel,
            actor: entry.actor,
            note: entry.note || null
          })));
        } catch (dbErr) {
          console.error('[DB INCIDENT INSERT ERROR]', dbErr.message);
        }
      }

      await logAudit('Citizen', 'SUBMIT', id, `Incident reported (${category})`);
      await broadcastUpdates(id);

      // Respond immediately with 201
      res.status(201).json({
        incident: hydrateIncident(newIncident),
        possibleDuplicateOf,
        warning: possibleDuplicateOf ? `Possible duplicate of active incident ${possibleDuplicateOf}` : undefined
      });

      // Fire AI Triage asynchronously (DO NOT AWAIT in request lifecycle)
      analyzeIncidentTriage({
        category,
        title: newIncident.title,
        description: newIncident.description,
        locationText,
        severity: newIncident.severity,
        mediaBuffer: req.file ? req.file.buffer : null,
        mediaMimeType: req.file ? req.file.mimetype : null
      }).then(async (triage) => {
        newIncident.aiSummary = triage.summary;
        newIncident.aiSuggestedSeverity = triage.suggestedSeverity;
        newIncident.aiRecommendedUnit = triage.recommendedUnit;
        newIncident.aiSpamScore = triage.spamRiskScore;

        if (supabase) {
          await supabase.from('incidents').update({
            ai_summary: triage.summary,
            ai_suggested_severity: triage.suggestedSeverity,
            ai_recommended_unit: triage.recommendedUnit,
            ai_spam_score: triage.spamRiskScore
          }).eq('id', id);
        }
        await broadcastUpdates(id);
      }).catch(aiErr => {
        console.error('[ASYNC AI TRIAGE ERROR]', aiErr.message);
      });

    } catch (err) {
      console.error('[POST INCIDENT ERROR]', err);
      if (!res.headersSent) {
        res.status(500).json({ error: 'Server Error', message: err.message });
      }
    }
  });

  // 2. GET /api/incidents/track/:id (PUBLIC)
  router.get('/track/:id', async (req, res) => {
    try {
      const { id } = req.params;
      const inc = inMemoryIncidents.get(id);

      if (!inc) {
        return res.status(404).json({ error: 'Not Found', message: `Incident ${id} not found` });
      }

      const hydrated = hydrateIncident(inc);
      delete hydrated.reporterContact; // Sanitize for public tracking

      return res.json(hydrated);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 3. GET /api/incidents (AUTHENTICATED)
  router.get('/', requireAuth(), async (req, res) => {
    try {
      const incidentsList = Array.from(inMemoryIncidents.values())
        .sort((a, b) => b.createdAt - a.createdAt)
        .map(inc => hydrateIncident(inc));

      return res.json(incidentsList);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 4. GET /api/incidents/:id/recommended-teams
  router.get('/:id/recommended-teams', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const inc = inMemoryIncidents.get(id);

      if (!inc) {
        return res.status(404).json({ error: 'Not Found', message: `Incident ${id} not found` });
      }

      if (supabase && inc.lat && inc.lng) {
        try {
          const { data: rpcTeams } = await supabase.rpc('rank_available_teams', {
            p_lat: inc.lat,
            p_lng: inc.lng,
            p_recommended_type: getRecommendedUnitType(inc.category)
          });

          if (rpcTeams && rpcTeams.length > 0) {
            return res.json({
              incidentId: id,
              category: inc.category,
              recommendedUnitType: getRecommendedUnitType(inc.category),
              teams: rpcTeams
            });
          }
        } catch (rErr) {
          console.error('[RPC RANK TEAMS ERROR]', rErr.message);
        }
      }

      const availableTeams = Array.from(inMemoryTeams.values());

      return res.json({
        incidentId: id,
        category: inc.category,
        recommendedUnitType: getRecommendedUnitType(inc.category),
        teams: availableTeams
      });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 5. POST /api/incidents/:id/verify
  router.post('/:id/verify', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { severity } = req.body;
      const inc = inMemoryIncidents.get(id);

      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });
      if (inc.status !== 'REPORTED') return res.status(400).json({ error: 'Invalid Transition', message: 'Only REPORTED incidents can be verified' });

      const now = Date.now();
      inc.status = 'VERIFIED';
      if (severity) inc.severity = severity;

      const actorName = req.user.name || req.user.role;
      const timeline = inMemoryTimeline.get(id) || [];
      timeline.push({ timestamp: now, statusLabel: 'Verified', actor: actorName });
      inMemoryTimeline.set(id, timeline);

      if (supabase) {
        try {
          await supabase.from('incidents').update({
            status: 'VERIFIED',
            severity: inc.severity
          }).eq('id', id);

          await supabase.from('incident_timeline').insert([{
            id: `TL-${now}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(now)),
            status_label: 'Verified',
            actor: actorName,
            note: null
          }]);
        } catch (dbErr) {
          console.error('[DB VERIFY INCIDENT ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'VERIFY', id, `Incident verified by ${req.user.email}`);

      // Auto-Dispatch Logic after Verification
      if (getAutoDispatchEnabled() && supabase && inc.lat && inc.lng) {
        try {
          const recType = getRecommendedUnitType(inc.category);
          const { data: rpcTeams } = await supabase.rpc('rank_available_teams', {
            p_lat: inc.lat,
            p_lng: inc.lng,
            p_recommended_type: recType
          });

          if (rpcTeams && rpcTeams.length > 0) {
            const autoAssignedTeam = rpcTeams[0]; // Pick closest available
            const teamLocal = inMemoryTeams.get(autoAssignedTeam.id);

            if (teamLocal && teamLocal.isAvailable) {
              const dispatchNow = Date.now();
              teamLocal.isAvailable = false;
              inc.status = 'ASSIGNED';
              inc.assignedTeamId = teamLocal.id;
              inc.assignedTeamName = teamLocal.name;
              inc.assignedAt = dispatchNow;

              const tmln = inMemoryTimeline.get(id);
              tmln.push({
                timestamp: dispatchNow,
                statusLabel: `Auto-Assigned to ${teamLocal.name}`,
                actor: 'Auto-Dispatch Engine'
              });
              inMemoryTimeline.set(id, tmln);

              await supabase.from('response_teams').update({ is_available: false }).eq('id', teamLocal.id);

              await supabase.from('incidents').update({
                status: 'ASSIGNED',
                assigned_team_id: teamLocal.id,
                assigned_team_name: teamLocal.name,
                assigned_at: new Date(dispatchNow).toISOString()
              }).eq('id', id);

              await supabase.from('incident_timeline').insert([{
                id: `TL-${dispatchNow}-${Math.floor(Math.random() * 1000)}`,
                incident_id: id,
                timestamp_label: formatIstTime(new Date(dispatchNow)),
                status_label: `Auto-Assigned to ${teamLocal.name}`,
                actor: 'Auto-Dispatch Engine',
                note: null
              }]);

              await logAudit('System', 'AUTO_DISPATCHED', id, `Auto-assigned to ${teamLocal.name} (${autoAssignedTeam.distance_km || 0}km)`);
            }
          }
        } catch (adErr) {
          console.error('[AUTO DISPATCH ERROR]', adErr.message);
        }
      }

      await broadcastUpdates(id);
      return res.json(hydrateIncident(inc));
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 6. POST /api/incidents/:id/reject
  router.post('/:id/reject', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { reason } = req.body;

      if (!reason || reason.trim().length === 0) {
        return res.status(400).json({ error: 'Validation Error', message: 'Rejection reason is required' });
      }

      const inc = inMemoryIncidents.get(id);
      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });
      if (['RESOLVED', 'REJECTED'].includes(inc.status)) {
        return res.status(400).json({ error: 'Invalid Transition', message: 'Resolved or Rejected incidents cannot be rejected again' });
      }

      // If assigned to a team, release the team
      if (inc.assignedTeamId) {
        const team = inMemoryTeams.get(inc.assignedTeamId);
        if (team) {
          team.isAvailable = true;
          if (supabase) {
            try {
              await supabase.from('response_teams').update({ is_available: true }).eq('id', team.id);
            } catch (dbErr) {
              console.error('[DB TEAM RELEASE ERROR ON REJECT]', dbErr.message);
            }
          }
        }
      }

      const now = Date.now();
      inc.status = 'REJECTED';
      inc.rejectionReason = reason;

      const actorName = req.user.name || req.user.role;
      const timeline = inMemoryTimeline.get(id) || [];
      timeline.push({ timestamp: now, statusLabel: 'Rejected', actor: actorName, note: reason });
      inMemoryTimeline.set(id, timeline);

      if (supabase) {
        try {
          await supabase.from('incidents').update({
            status: 'REJECTED',
            rejection_reason: reason
          }).eq('id', id);

          await supabase.from('incident_timeline').insert([{
            id: `TL-${now}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(now)),
            status_label: 'Rejected',
            actor: actorName,
            note: reason
          }]);
        } catch (dbErr) {
          console.error('[DB REJECT INCIDENT ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'REJECT', id, `Rejected: ${reason}`);
      await broadcastUpdates(id);

      return res.json(hydrateIncident(inc));
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 7. POST /api/incidents/:id/assign
  router.post('/:id/assign', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { teamId } = req.body;

      if (!teamId) return res.status(400).json({ error: 'Validation Error', message: 'teamId is required' });

      const inc = inMemoryIncidents.get(id);
      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });
      if (inc.status !== 'VERIFIED') return res.status(400).json({ error: 'Invalid Transition', message: 'Only VERIFIED incidents can be assigned' });

      const team = inMemoryTeams.get(teamId);
      if (!team) return res.status(404).json({ error: 'Not Found', message: 'Team not found' });
      if (!team.isAvailable) return res.status(400).json({ error: 'Team Busy', message: 'Team is not available for assignment' });

      const now = Date.now();
      team.isAvailable = false;
      inc.status = 'ASSIGNED';
      inc.assignedTeamId = team.id;
      inc.assignedTeamName = team.name;
      inc.assignedAt = now;

      const actorName = req.user.name || req.user.role;
      const timeline = inMemoryTimeline.get(id) || [];
      timeline.push({ timestamp: now, statusLabel: `Assigned to ${team.name}`, actor: actorName });
      inMemoryTimeline.set(id, timeline);

      if (supabase) {
        try {
          await supabase.from('incidents').update({
            status: 'ASSIGNED',
            assigned_team_id: team.id,
            assigned_team_name: team.name,
            assigned_at: new Date(now).toISOString()
          }).eq('id', id);

          await supabase.from('response_teams').update({
            is_available: false
          }).eq('id', team.id);

          await supabase.from('incident_timeline').insert([{
            id: `TL-${now}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(now)),
            status_label: `Assigned to ${team.name}`,
            actor: actorName,
            note: null
          }]);
        } catch (dbErr) {
          console.error('[DB ASSIGN INCIDENT ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'ASSIGN', id, `Assigned to ${team.name}`);
      await broadcastUpdates(id);

      return res.json(hydrateIncident(inc));
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 8. POST /api/incidents/:id/status
  router.post('/:id/status', requireAuth('RESPONDER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { note } = req.body;

      const inc = inMemoryIncidents.get(id);
      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });

      // Enforce team ownership unless ADMIN
      if (req.user.role === 'RESPONDER' && req.user.assigned_team_id !== inc.assignedTeamId) {
        await logAudit('Responder', 'ACCESS_DENIED_403', id, `Team ${req.user.assigned_team_id} attempted unauthorized status update`);
        return res.status(403).json({ error: 'Access Denied', message: 'ACCESS_DENIED_403: Not assigned to this incident' });
      }

      const nextStatusMap = {
        ASSIGNED: 'ACCEPTED',
        ACCEPTED: 'EN_ROUTE',
        EN_ROUTE: 'ON_SITE',
        ON_SITE: 'RESPONDING',
        RESPONDING: 'RESOLVED'
      };

      const nextStatus = nextStatusMap[inc.status];
      if (!nextStatus) {
        return res.status(400).json({ error: 'Invalid Transition', message: `Cannot advance from status ${inc.status}` });
      }

      const now = Date.now();
      inc.status = nextStatus;

      const actorName = req.user.name || inc.assignedTeamName || 'Responder';
      const timeline = inMemoryTimeline.get(id) || [];
      timeline.push({ timestamp: now, statusLabel: `Status updated to ${nextStatus}`, actor: actorName });
      inMemoryTimeline.set(id, timeline);

      if (note && note.trim().length > 0) {
        const fieldNotes = inMemoryNotes.get(id) || [];
        fieldNotes.push({ timestamp: now, author: actorName, text: note });
        inMemoryNotes.set(id, fieldNotes);

        if (supabase) {
          try {
            await supabase.from('incident_notes').insert([{
              id: `NOTE-${now}-${Math.floor(Math.random() * 1000)}`,
              incident_id: id,
              timestamp_label: formatIstTime(new Date(now)),
              author: actorName,
              text: note
            }]);
          } catch (nErr) {
            console.error('[DB INCIDENT NOTE INSERT ERROR]', nErr.message);
          }
        }
      }

      if (nextStatus === 'RESOLVED') {
        const team = inMemoryTeams.get(inc.assignedTeamId);
        if (team) {
          team.isAvailable = true;
          team.resolvedCount = (team.resolvedCount || 0) + 1;

          if (supabase) {
            try {
              await supabase.from('response_teams').update({
                is_available: true,
                resolved_count: team.resolvedCount
              }).eq('id', team.id);
            } catch (tErr) {
              console.error('[DB TEAM RESOLVED UPDATE ERROR]', tErr.message);
            }
          }
        }

        if (supabase) {
          try {
            await supabase.from('incidents').update({
              status: 'RESOLVED',
              resolved_at: new Date(now).toISOString()
            }).eq('id', id);
          } catch (iErr) {
            console.error('[DB INCIDENT RESOLVE UPDATE ERROR]', iErr.message);
          }
        }

        await logAudit('Responder', 'RESOLVE', id, `Incident resolved by ${inc.assignedTeamName}`);
      } else {
        if (supabase) {
          try {
            await supabase.from('incidents').update({
              status: nextStatus
            }).eq('id', id);
          } catch (iErr) {
            console.error('[DB INCIDENT STATUS UPDATE ERROR]', iErr.message);
          }
        }

        await logAudit('Responder', 'STATUS_UPDATE', id, `Advanced to ${nextStatus}`);
      }

      if (supabase) {
        try {
          await supabase.from('incident_timeline').insert([{
            id: `TL-${now}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(now)),
            status_label: `Status updated to ${nextStatus}`,
            actor: actorName,
            note: null
          }]);
        } catch (tlErr) {
          console.error('[DB TIMELINE INSERT ERROR]', tlErr.message);
        }
      }

      await broadcastUpdates(id);
      return res.json(hydrateIncident(inc));
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 9. POST /api/incidents/:id/notes
  router.post('/:id/notes', requireAuth('RESPONDER', 'DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { text } = req.body;

      if (!text || text.trim().length === 0) {
        return res.status(400).json({ error: 'Validation Error', message: 'Note text is required' });
      }

      const inc = inMemoryIncidents.get(id);
      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });

      const now = Date.now();
      const authorName = req.user.name || req.user.role;
      const fieldNotes = inMemoryNotes.get(id) || [];
      const newNote = { timestamp: now, author: authorName, text };
      fieldNotes.push(newNote);
      inMemoryNotes.set(id, fieldNotes);

      if (supabase) {
        try {
          await supabase.from('incident_notes').insert([{
            id: `NOTE-${now}-${Math.floor(Math.random() * 1000)}`,
            incident_id: id,
            timestamp_label: formatIstTime(new Date(now)),
            author: authorName,
            text
          }]);
        } catch (dbErr) {
          console.error('[DB NOTE INSERT ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'FIELD_NOTE', id, `Note added by ${authorName}`);
      await broadcastUpdates(id);

      return res.status(201).json({ message: 'Note added', note: newNote });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 10. POST /api/incidents/:id/ai-triage
  router.post('/:id/ai-triage', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const inc = inMemoryIncidents.get(id);
      if (!inc) return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });

      const triage = await analyzeIncidentTriage({
        category: inc.category,
        title: inc.title,
        description: inc.description,
        locationText: inc.locationText,
        severity: inc.severity,
        mediaBuffer: null,
        mediaMimeType: null
      });

      inc.aiSummary = triage.summary;
      inc.aiSuggestedSeverity = triage.suggestedSeverity;
      inc.aiRecommendedUnit = triage.recommendedUnit;
      inc.aiSpamScore = triage.spamRiskScore;
      inMemoryIncidents.set(id, inc);

      if (supabase) {
        try {
          await supabase.from('incidents').update({
            ai_summary: triage.summary,
            ai_suggested_severity: triage.suggestedSeverity,
            ai_recommended_unit: triage.recommendedUnit,
            ai_spam_score: triage.spamRiskScore
          }).eq('id', id);
        } catch (dbErr) {
          console.error('[DB AI TRIAGE UPDATE ERROR]', dbErr.message);
        }
      }

      await broadcastUpdates(id);
      return res.json(hydrateIncident(inc));
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  return router;
}

function getRecommendedUnitType(category) {
  switch (category) {
    case 'ROAD_ACCIDENT': return 'Traffic & Rescue';
    case 'FIRE_HAZARD': return 'Fire & Hazmat';
    case 'MEDICAL': return 'Medical EMS';
    case 'PUBLIC_SAFETY': return 'Security Response';
    default: return 'General Response';
  }
}
