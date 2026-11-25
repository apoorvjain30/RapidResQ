import express from 'express';
import { supabase } from '../config/supabase.js';
import { inMemoryUsers } from '../config/seed.js';
import { inMemoryTeams, inMemoryIncidents } from './incidentRoutes.js';
import { logAudit, verifyAuditChain } from '../services/audit.js';
import { requireAuth } from '../middleware/auth.js';

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

      // Broadcast to staff room
      io.to('staff').emit('team:location', locationPayload);

      // Broadcast to active tracking rooms
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

  // 6. PATCH /api/admin/staff/:id/toggle
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

  // 7. GET /api/admin/audit
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

      // Return empty array if DB not connected yet
      return res.json([]);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 8. GET /api/admin/audit/verify
  router.get('/audit/verify', requireAuth('ADMIN', 'DISPATCHER'), async (req, res) => {
    try {
      const result = await verifyAuditChain();
      return res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  // 9. GET /api/admin/analytics
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
