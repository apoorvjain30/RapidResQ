import express from 'express';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { supabase } from '../config/supabase.js';
import { inMemoryUsers } from '../config/seed.js';
import { inMemoryTeams, inMemoryIncidents, inMemoryTimeline, inMemoryNotes, hydrateIncident } from './incidentRoutes.js';
import { logAudit, verifyAuditChain } from '../services/audit.js';
import { requireAuth } from '../middleware/auth.js';

let autoDispatchEnabled = false;

export function createAdminRoutes(io) {
  const router = express.Router();

  // 1. GET /api/admin/teams
  router.get('/teams', requireAuth('DISPATCHER', 'RESPONDER', 'ADMIN'), async (req, res) => {
    try {
      const teamsList = Array.from(inMemoryTeams.values());
      return res.json(teamsList);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 2. POST /api/admin/teams
  router.post('/teams', requireAuth('ADMIN'), async (req, res) => {
    try {
      const { name, unitType, baseLocation, lat, lng } = req.body;

      if (!name || !unitType || !baseLocation) {
        return res.status(400).json({ error: 'Validation Error', message: 'name, unitType, and baseLocation are required' });
      }

      const teamId = `TEAM-${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
      const newTeam = {
        id: teamId,
        name,
        unitType,
        baseLocation,
        lat: parseFloat(lat) || 22.7196,
        lng: parseFloat(lng) || 75.8577,
        isAvailable: true,
        resolvedCount: 0
      };

      inMemoryTeams.set(teamId, newTeam);

      if (supabase) {
        try {
          await supabase.from('response_teams').insert([{
            id: newTeam.id,
            name: newTeam.name,
            unit_type: newTeam.unitType,
            base_location: newTeam.baseLocation,
            lat: newTeam.lat,
            lng: newTeam.lng,
            is_available: true,
            resolved_count: 0
          }]);
        } catch (dbErr) {
          console.error('[ADMIN DB INSERT TEAM ERROR]', dbErr.message);
        }
      }

      await logAudit('Administrator', 'TEAM_CREATED', teamId, `Added ${name} (${unitType})`);
      io.to('staff').emit('teams:updated', Array.from(inMemoryTeams.values()));

      return res.status(201).json(newTeam);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 3. PATCH /api/admin/teams/:id/toggle
  router.patch('/teams/:id/toggle', requireAuth('ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const team = inMemoryTeams.get(id);

      if (!team) {
        return res.status(404).json({ error: 'Not Found', message: `Team ${id} not found` });
      }

      team.isAvailable = !team.isAvailable;
      inMemoryTeams.set(id, team);

      if (supabase) {
        try {
          await supabase.from('response_teams').update({
            is_available: team.isAvailable
          }).eq('id', id);
        } catch (dbErr) {
          console.error('[ADMIN DB TOGGLE TEAM ERROR]', dbErr.message);
        }
      }

      const statusStr = team.isAvailable ? 'Available' : 'Busy';
      await logAudit('Administrator', 'TEAM_STATUS_CHANGED', id, `Marked as ${statusStr}`);
      io.to('staff').emit('teams:updated', Array.from(inMemoryTeams.values()));

      return res.json(team);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 4. PATCH /api/admin/teams/:id/location
  router.patch('/teams/:id/location', requireAuth('RESPONDER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { lat, lng } = req.body;

      const numLat = parseFloat(lat);
      const numLng = parseFloat(lng);

      if (isNaN(numLat) || numLat < -90 || numLat > 90 || isNaN(numLng) || numLng < -180 || numLng > 180) {
        return res.status(400).json({ error: 'Validation Error', message: 'Valid numeric lat (-90 to 90) and lng (-180 to 180) are required' });
      }

      if (req.user.role === 'RESPONDER' && req.user.assigned_team_id !== id) {
        await logAudit('Responder', 'ACCESS_DENIED_403', id, `Team ${req.user.assigned_team_id} attempted unauthorized location update for ${id}`);
        return res.status(403).json({ error: 'Access Denied', message: 'ACCESS_DENIED_403: Cannot update location for another unit' });
      }

      const team = inMemoryTeams.get(id);
      if (!team) {
        return res.status(404).json({ error: 'Not Found', message: `Team ${id} not found` });
      }

      team.lat = numLat;
      team.lng = numLng;
      inMemoryTeams.set(id, team);

      if (supabase) {
        try {
          await supabase.from('response_teams').update({
            lat: numLat,
            lng: numLng
          }).eq('id', id);
        } catch (dbErr) {
          console.error('[ADMIN DB LOCATION UPDATE ERROR]', dbErr.message);
        }
      }

      const locationPayload = { teamId: id, name: team.name, lat: numLat, lng: numLng };

      io.to('staff').emit('team:location', locationPayload);

      for (const incident of inMemoryIncidents.values()) {
        if (incident.assignedTeamId === id && !['RESOLVED', 'REJECTED'].includes(incident.status)) {
          io.to(`track:${incident.id}`).emit('team:location', { ...locationPayload, incidentId: incident.id });
        }
      }

      return res.json({ message: 'Location updated', team });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 5. GET /api/admin/staff
  router.get('/staff', requireAuth('ADMIN'), async (req, res) => {
    try {
      const staffList = Array.from(inMemoryUsers.values()).map(user => {
        const copy = { ...user };
        delete copy.password_hash;
        return copy;
      });

      return res.json(staffList);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 6. POST /api/admin/staff
  router.post('/staff', requireAuth('ADMIN'), async (req, res) => {
    try {
      const { name, email, phone, password, role, department, assignedTeamId } = req.body;

      if (!name || !email || !password || !role) {
        return res.status(400).json({ error: 'Validation Error', message: 'Name, email, password, and role are required' });
      }

      if (!['ADMIN', 'DISPATCHER', 'RESPONDER'].includes(role)) {
        return res.status(400).json({ error: 'Validation Error', message: 'Invalid staff role' });
      }

      const emailLower = email.toLowerCase();
      if (inMemoryUsers.has(emailLower)) {
        return res.status(400).json({ error: 'Validation Error', message: 'Email already exists' });
      }

      const passRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
      if (!passRegex.test(password)) {
        return res.status(400).json({ error: 'Validation Error', message: 'Password must be at least 8 chars with upper, lower, and digit' });
      }

      const salt = await bcrypt.genSalt(10);
      const hash = await bcrypt.hash(password, salt);

      const userId = `USR-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;

      const newUser = {
        id: userId,
        name,
        email: emailLower,
        phone: phone || '',
        password_hash: hash,
        role,
        department: department || '',
        assigned_team_id: role === 'RESPONDER' ? (assignedTeamId || null) : null,
        mfa_enabled: true,
        is_active: true,
        failed_attempts: 0,
        locked_until: null,
        created_at: new Date().toISOString()
      };

      inMemoryUsers.set(emailLower, newUser);

      if (supabase) {
        try {
          await supabase.from('users').insert([{
            id: newUser.id,
            name: newUser.name,
            email: newUser.email,
            phone: newUser.phone,
            password_hash: newUser.password_hash,
            role: newUser.role,
            department: newUser.department,
            assigned_team_id: newUser.assigned_team_id,
            mfa_enabled: newUser.mfa_enabled,
            is_active: newUser.is_active,
            failed_attempts: newUser.failed_attempts,
            locked_until: newUser.locked_until,
            created_at: newUser.created_at
          }]);
        } catch (dbErr) {
          console.error('[ADMIN DB INSERT STAFF ERROR]', dbErr.message);
        }
      }

      await logAudit('Administrator', 'STAFF_CREATED', userId, `Created ${role} account for ${emailLower}`);
      io.to('staff').emit('incidents:updated', {}); // Trigger generic UI refresh

      const sanitized = { ...newUser };
      delete sanitized.password_hash;
      return res.status(201).json(sanitized);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 7. PATCH /api/admin/staff/:id/role
  router.patch('/staff/:id/role', requireAuth('ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const { role, department, assignedTeamId } = req.body;

      if (!['CITIZEN', 'RESPONDER', 'DISPATCHER', 'ADMIN'].includes(role)) {
        return res.status(400).json({ error: 'Validation Error', message: 'Invalid role' });
      }

      if (req.user.id === id && role !== 'ADMIN') {
        return res.status(400).json({ error: 'Validation Error', message: 'Administrators cannot demote themselves' });
      }

      let targetUser = null;
      for (const user of inMemoryUsers.values()) {
        if (user.id === id) {
          targetUser = user;
          break;
        }
      }

      if (!targetUser) {
        return res.status(404).json({ error: 'Not Found', message: `Staff member ${id} not found` });
      }

      targetUser.role = role;
      if (department !== undefined) targetUser.department = department;
      targetUser.assigned_team_id = role === 'RESPONDER' ? (assignedTeamId || null) : null;

      inMemoryUsers.set(targetUser.email, targetUser);

      if (supabase) {
        try {
          await supabase.from('users').update({
            role: targetUser.role,
            department: targetUser.department,
            assigned_team_id: targetUser.assigned_team_id
          }).eq('id', id);
        } catch (dbErr) {
          console.error('[ADMIN DB ROLE STAFF ERROR]', dbErr.message);
        }
      }

      await logAudit('Administrator', 'STAFF_ROLE_UPDATED', id, `Updated role to ${role}`);
      io.to('staff').emit('incidents:updated', {});

      const sanitized = { ...targetUser };
      delete sanitized.password_hash;
      return res.json(sanitized);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 8. PATCH /api/admin/staff/:id/toggle
  router.patch('/staff/:id/toggle', requireAuth('ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      let targetUser = null;

      for (const user of inMemoryUsers.values()) {
        if (user.id === id) {
          targetUser = user;
          break;
        }
      }

      if (!targetUser) {
        return res.status(404).json({ error: 'Not Found', message: `Staff member ${id} not found` });
      }

      targetUser.is_active = !targetUser.is_active;
      inMemoryUsers.set(targetUser.email.toLowerCase(), targetUser);

      if (supabase) {
        try {
          await supabase.from('users').update({
            is_active: targetUser.is_active
          }).eq('id', id);
        } catch (dbErr) {
          console.error('[ADMIN DB TOGGLE STAFF ERROR]', dbErr.message);
        }
      }

      const actionStr = targetUser.is_active ? 'Activated' : 'Suspended';
      await logAudit('Administrator', 'STAFF_ACCESS_UPDATED', id, `Account ${actionStr}`);

      const sanitized = { ...targetUser };
      delete sanitized.password_hash;

      return res.json(sanitized);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 9. GET /api/admin/settings
  router.get('/settings', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      if (supabase) {
        try {
          const { data, error } = await supabase.from('system_settings').select('auto_dispatch_enabled').single();
          if (!error && data) {
            autoDispatchEnabled = data.auto_dispatch_enabled;
          }
        } catch (dbErr) {
          console.error('[ADMIN FETCH SETTINGS ERROR]', dbErr.message);
        }
      }
      return res.json({ autoDispatchEnabled });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 10. PATCH /api/admin/settings
  router.patch('/settings', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { autoDispatchEnabled: enabled } = req.body;
      if (typeof enabled !== 'boolean') {
        return res.status(400).json({ error: 'Validation Error', message: 'autoDispatchEnabled boolean is required' });
      }

      autoDispatchEnabled = enabled;

      if (supabase) {
        try {
          await supabase.from('system_settings').upsert({ id: 1, auto_dispatch_enabled: autoDispatchEnabled });
        } catch (dbErr) {
          console.error('[ADMIN UPDATE SETTINGS ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'AUTO_DISPATCH_TOGGLED', 'SYSTEM', `Auto-Dispatch set to ${autoDispatchEnabled}`);
      io.to('staff').emit('settings:updated', { autoDispatchEnabled });

      return res.json({ autoDispatchEnabled });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 11. DELETE /api/admin/incidents/:id
  router.delete('/incidents/:id', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      const { id } = req.params;
      const inc = inMemoryIncidents.get(id);

      if (!inc) {
        return res.status(404).json({ error: 'Not Found', message: 'Incident not found' });
      }

      // If incident is actively assigned to a team, release the team
      if (inc.assignedTeamId && !['RESOLVED', 'REJECTED'].includes(inc.status)) {
        const team = inMemoryTeams.get(inc.assignedTeamId);
        if (team) {
          team.isAvailable = true;
          inMemoryTeams.set(team.id, team);
          if (supabase) {
            try {
              await supabase.from('response_teams').update({ is_available: true }).eq('id', team.id);
            } catch (dbErr) {
              console.error('[ADMIN DB TEAM RELEASE ERROR]', dbErr.message);
            }
          }
        }
      }

      inMemoryIncidents.delete(id);
      inMemoryTimeline.delete(id);
      inMemoryNotes.delete(id);

      if (supabase) {
        try {
          await supabase.from('incident_notes').delete().eq('incident_id', id);
          await supabase.from('incident_timeline').delete().eq('incident_id', id);
          await supabase.from('incidents').delete().eq('id', id);
        } catch (dbErr) {
          console.error('[ADMIN DB INCIDENT DELETE ERROR]', dbErr.message);
        }
      }

      await logAudit(req.user.role, 'INCIDENT_DELETED', id, `Incident deleted by ${req.user.role}`);
      io.to('staff').emit('incidents:updated', { id, deleted: true });

      return res.json({ message: 'Incident deleted successfully' });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 12. GET /api/admin/audit
  router.get('/audit', requireAuth('DISPATCHER', 'ADMIN'), async (req, res) => {
    try {
      if (supabase) {
        try {
          const { data, error } = await supabase
            .from('audit_logs')
            .select('*')
            .order('created_at', { ascending: false })
            .limit(200);

          if (!error && data) {
            return res.json(data);
          }
        } catch (dbErr) {
          console.error('[ADMIN DB AUDIT FETCH ERROR]', dbErr.message);
        }
      }

      return res.json([]);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 13. GET /api/admin/audit/verify
  router.get('/audit/verify', requireAuth('ADMIN', 'DISPATCHER'), async (req, res) => {
    try {
      const result = await verifyAuditChain();
      return res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 14. GET /api/admin/analytics
  router.get('/analytics', requireAuth('ADMIN', 'DISPATCHER'), async (req, res) => {
    try {
      const incidents = Array.from(inMemoryIncidents.values());
      const teams = Array.from(inMemoryTeams.values());

      const totalIncidents = incidents.length;
      const activeIncidents = incidents.filter(i => !['RESOLVED', 'REJECTED'].includes(i.status)).length;
      const resolvedIncidents = incidents.filter(i => i.status === 'RESOLVED').length;
      const rejectedIncidents = incidents.filter(i => i.status === 'REJECTED').length;

      let totalEscalations = 0;
      const byCategory = {};
      const bySeverity = {};
      const byStatus = {};

      incidents.forEach(i => {
        byCategory[i.category] = (byCategory[i.category] || 0) + 1;
        bySeverity[i.severity] = (bySeverity[i.severity] || 0) + 1;
        byStatus[i.status] = (byStatus[i.status] || 0) + 1;
      });

      const topUnits = teams.map(t => ({
        id: t.id,
        name: t.name,
        unitType: t.unitType,
        resolvedCount: t.resolvedCount || 0,
        isAvailable: t.isAvailable
      })).sort((a, b) => b.resolvedCount - a.resolvedCount);

      return res.json({
        totalIncidents,
        activeIncidents,
        resolvedIncidents,
        rejectedIncidents,
        totalEscalations,
        byCategory,
        bySeverity,
        byStatus,
        topUnits
      });
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  return router;
}

export function getAutoDispatchEnabled() {
  return autoDispatchEnabled;
}
