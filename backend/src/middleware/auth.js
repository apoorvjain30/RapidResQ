import jwt from 'jsonwebtoken';
import { logAudit } from '../services/audit.js';

export function requireAuth(...allowedRoles) {
  return async (req, res, next) => {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Missing or invalid Authorization header'
      });
    }

    const token = authHeader.split(' ')[1];
    const jwtSecret = process.env.JWT_SECRET || 'your_jwt_secret_key_here';

    try {
      const decoded = jwt.verify(token, jwtSecret);
      req.user = decoded;

      if (allowedRoles.length > 0 && !allowedRoles.includes(decoded.role)) {
        const incidentId = req.params?.id || 'SYSTEM';
        await logAudit(
          decoded.role,
          'ACCESS_DENIED_403',
          incidentId,
          `Blocked ${req.method} ${req.originalUrl}`
        );

        return res.status(403).json({
          error: 'Forbidden',
          message: `Access denied. Role '${decoded.role}' is not authorized for this action.`
        });
      }

      next();
    } catch (err) {
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Token is invalid or expired',
        details: err.message
      });
    }
  };
}
