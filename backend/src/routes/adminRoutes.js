import express from 'express';
import { supabase } from '../config/supabase.js';
import { inMemoryUsers } from '../config/seed.js';
import { inMemoryTeams } from './incidentRoutes.js';
import { logAudit } from '../services/audit.js';
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
        lat: parseFloat(lat) || null,
        lng: parseFloat(lng) || null,
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

  // 4. GET /api/admin/staff
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

  // 5. PATCH /api/admin/staff/:id/toggle
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

  // 6. GET /api/admin/audit
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

  return router;
}
