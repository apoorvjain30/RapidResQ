import 'dotenv/config';
import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import helmet from 'helmet';
import cors from 'cors';
import jwt from 'jsonwebtoken';

import { seedDefaultUsers } from './config/seed.js';
import { startEscalationMonitor } from './services/escalation.js';

import authRoutes from './routes/authRoutes.js';
import { createIncidentRoutes } from './routes/incidentRoutes.js';
import { createAdminRoutes } from './routes/adminRoutes.js';
import { createAiRoutes } from './routes/aiRoutes.js';

const app = express();
const server = http.createServer(app);

const PORT = process.env.PORT || 4000;
const JWT_SECRET = process.env.JWT_SECRET || 'your_jwt_secret_key_here';

// Socket.IO Server Initialization
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE']
  }
});

// Express Middlewares
app.use(helmet({
  contentSecurityPolicy: false // Disable CSP for API server ease
}));
app.use(cors());
app.use(express.json({ limit: '5mb' }));
app.use(express.urlencoded({ extended: true, limit: '5mb' }));

// Health Check Endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'RapidResQ Backend API',
    environment: process.env.NODE_ENV || 'development',
    timestamp: new Date().toISOString()
  });
});

// Mount Routes
app.use('/api/auth', authRoutes);
app.use('/api/incidents', createIncidentRoutes(io));
app.use('/api/admin', createAdminRoutes(io));
app.use('/api/ai', createAiRoutes());

// Socket.IO Authentication & Room Management
io.on('connection', (socket) => {
  console.log(`[SOCKET] Client connected: ${socket.id}`);

  socket.on('join:staff', (data) => {
    try {
      const token = typeof data === 'string' ? data : data?.token;
      if (token) {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (['DISPATCHER', 'RESPONDER', 'ADMIN'].includes(decoded.role)) {
          socket.join('staff');
          console.log(`[SOCKET] ${socket.id} joined 'staff' room (${decoded.role}: ${decoded.email})`);
          socket.emit('joined:staff', { status: 'success', role: decoded.role });
          return;
        }
      }
      // Allow unauthenticated demo join in dev mode if explicitly requested
      if (process.env.NODE_ENV !== 'production') {
        socket.join('staff');
        console.log(`[SOCKET] ${socket.id} joined 'staff' room (dev mode demo join)`);
        socket.emit('joined:staff', { status: 'success', role: 'DEMO' });
      } else {
        socket.emit('error', { message: 'Unauthorized staff room join attempt' });
      }
    } catch (err) {
      console.error(`[SOCKET JOIN ERROR] ${socket.id}:`, err.message);
      socket.emit('error', { message: 'Authentication failed for staff room' });
    }
  });

  socket.on('join:track', (data) => {
    const incidentId = typeof data === 'string' ? data : data?.incidentId;
    if (incidentId) {
      socket.join(`track:${incidentId}`);
      console.log(`[SOCKET] ${socket.id} joined 'track:${incidentId}' room`);
      socket.emit('joined:track', { status: 'success', incidentId });
    }
  });

  socket.on('disconnect', (reason) => {
    console.log(`[SOCKET] Client disconnected: ${socket.id} (${reason})`);
  });
});

// Startup Bootstrap Sequence
async function startServer() {
  try {
    await seedDefaultUsers();
    startEscalationMonitor(io);

    server.listen(PORT, () => {
      console.log(`\n==================================================`);
      console.log(` RapidResQ Emergency Backend Server Running`);
      console.log(` Port: ${PORT}`);
      console.log(` Mode: ${process.env.NODE_ENV || 'development'}`);
      console.log(` Socket.IO: Active`);
      console.log(` Escalation Monitor: Running (15s interval)`);
      console.log(`==================================================\n`);
    });
  } catch (err) {
    console.error('[FATAL SERVER STARTUP ERROR]', err);
    process.exit(1);
  }
}

startServer();
