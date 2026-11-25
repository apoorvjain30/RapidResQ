import express from 'express';
import rateLimit from 'express-rate-limit';
import jwt from 'jsonwebtoken';
import { supabase } from '../config/supabase.js';
import { generateRoleScopedChat } from '../services/geminiService.js';
import { verifyAuditChain, logAudit } from '../services/audit.js';
import { inMemoryIncidents, inMemoryTeams, inMemoryTimeline } from './incidentRoutes.js';
import { inMemoryUsers } from '../config/seed.js';

const JWT_SECRET = process.env.JWT_SECRET || 'your_jwt_secret_key_here';

const aiChatRateLimiter = rateLimit({
  windowMs: 10 * 60 * 1000, // 10 min
  max: 25,
  message: { error: 'Rate Limit Exceeded', message: 'Too many AI chat queries. Please wait a few minutes.' }
});

export function createAiRoutes() {
  const router = express.Router();

  router.post('/chat', aiChatRateLimiter, async (req, res) => {
    try {
      const { message, incidentId } = req.body;

      if (!message || message.trim().length === 0) {
        return res.status(400).json({ error: 'Validation Error', message: 'Message is required' });
      }

      let effectiveRole = 'CITIZEN';
      let user = null;
      let scopeLabel = 'Citizen Public Scope';

      const authHeader = req.headers.authorization;
      if (authHeader && authHeader.startsWith('Bearer ')) {
        const token = authHeader.split(' ')[1];
        try {
          const decoded = jwt.verify(token, JWT_SECRET);
          if (decoded && decoded.role) {
            effectiveRole = decoded.role;
            user = decoded;
            scopeLabel = `${effectiveRole} Secure Scope`;
          }
        } catch (e) {
          // Fallback to Citizen if token invalid
        }
      }

      let contextData = {};

      switch (effectiveRole) {
        case 'CITIZEN': {
          scopeLabel = incidentId ? `Citizen Incident Scope (${incidentId})` : 'Citizen General Scope';
          let targetIncident = null;

          if (incidentId) {
            const inc = inMemoryIncidents.get(incidentId);
            if (inc) {
              targetIncident = {
                id: inc.id,
                category: inc.category,
                title: inc.title,
                status: inc.status,
                locationText: inc.locationText,
                assignedTeamName: inc.assignedTeamName,
                createdAt: inc.createdAt,
                timeline: (inMemoryTimeline.get(incidentId) || []).slice(-5)
              };
            }
          }
          contextData = {
            role: 'CITIZEN',
            trackedIncident: targetIncident,
            generalGuidelines: 'Dial 112 for immediate life-threatening emergencies. Use RapidResQ to track status and receive safety tips.'
          };
          break;
        }

        case 'RESPONDER': {
          scopeLabel = `Responder Unit Scope (${user?.assigned_team_id || 'Assigned'})`;
          let assignedInc = null;
          let teamDetails = user?.assigned_team_id ? inMemoryTeams.get(user.assigned_team_id) : null;

          for (const inc of inMemoryIncidents.values()) {
            if (inc.assignedTeamId === user?.assigned_team_id && !['RESOLVED', 'REJECTED'].includes(inc.status)) {
              assignedInc = {
                id: inc.id,
                category: inc.category,
                title: inc.title,
                description: inc.description,
                locationText: inc.locationText,
                severity: inc.severity,
                status: inc.status
              };
              break;
            }
          }

          contextData = {
            role: 'RESPONDER',
            assignedUnit: teamDetails,
            activeAssignment: assignedInc
          };
          break;
        }

        case 'DISPATCHER': {
          scopeLabel = 'Dispatcher Command Scope';
          const activeIncidents = Array.from(inMemoryIncidents.values())
            .filter(i => !['RESOLVED', 'REJECTED'].includes(i.status))
            .slice(0, 15)
            .map(i => ({
              id: i.id,
              category: i.category,
              title: i.title,
              severity: i.severity,
              status: i.status,
              assignedTeamName: i.assignedTeamName,
              aiSummary: i.aiSummary
            }));

          const teams = Array.from(inMemoryTeams.values());

          contextData = {
            role: 'DISPATCHER',
            activeIncidentsCount: activeIncidents.length,
            activeIncidents,
            availableTeams: teams.filter(t => t.isAvailable)
          };
          break;
        }

        case 'ADMIN': {
          scopeLabel = 'Admin Governance & Security Scope';
          const totalIncidents = inMemoryIncidents.size;
          const teams = Array.from(inMemoryTeams.values());
          const staffCount = inMemoryUsers.size;
          const auditVerify = await verifyAuditChain();

          contextData = {
            role: 'ADMIN',
            totalIncidents,
            totalTeams: teams.length,
            availableTeamsCount: teams.filter(t => t.isAvailable).length,
            staffCount,
            auditIntegrity: auditVerify
          };
          break;
        }
      }

      const reply = await generateRoleScopedChat({
        role: effectiveRole,
        userMessage: message,
        contextData
      });

      if (effectiveRole !== 'CITIZEN') {
        await logAudit(effectiveRole, 'AI_CHAT_QUERY', incidentId || 'SYSTEM', `AI query: "${message.substring(0, 60)}"`);
      }

      return res.json({
        reply,
        scopeLabel,
        modelUsed: process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite'
      });
    } catch (err) {
      console.error('[AI CHAT ROUTE ERROR]', err);
      res.status(500).json({ error: 'Server Error', message: err.message });
    }
  });

  return router;
}
