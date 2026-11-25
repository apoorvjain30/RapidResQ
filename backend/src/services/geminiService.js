import { GoogleGenAI } from '@google/genai';

const apiKey = process.env.GEMINI_API_KEY;
const MODEL = process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite';

const ai = apiKey ? new GoogleGenAI({ apiKey }) : null;

export async function analyzeIncidentTriage({ category, title, description, locationText, severity, mediaBuffer, mediaMimeType }) {
  const fallbackUnit = getRecommendedUnitType(category);
  const fallback = {
    summary: `${title}: Reported at ${locationText}. Requires immediate operational dispatch assessment.`,
    suggestedSeverity: severity || 'HIGH',
    recommendedUnit: fallbackUnit,
    spamRiskScore: 5
  };

  if (!ai) {
    return fallback;
  }

  try {
    const prompt = `Analyze this emergency incident report and return STRICT JSON with keys: summary (string, 2 sentences max), suggestedSeverity (CRITICAL|HIGH|MEDIUM|LOW), recommendedUnit (Medical EMS|Fire & Hazmat|Traffic & Rescue|Security Response), spamRiskScore (integer 0-100).
Category: ${category}
Title: ${title}
Description: ${description}
Location: ${locationText}
Initial Severity: ${severity}
Return ONLY valid JSON without markdown code fences.`;

    const contents = [];
    if (mediaBuffer && mediaMimeType && mediaBuffer.length > 0 && mediaBuffer.length < 4 * 1024 * 1024) {
      contents.push({
        inlineData: {
          data: mediaBuffer.toString('base64'),
          mimeType: mediaMimeType
        }
      });
    }
    contents.push(prompt);

    const response = await ai.models.generateContent({
      model: MODEL,
      contents: contents
    });

    const text = response.text || '';
    const cleanJson = text.replace(/```json/g, '').replace(/```/g, '').trim();
    const parsed = JSON.parse(cleanJson);

    return {
      summary: parsed.summary || fallback.summary,
      suggestedSeverity: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(parsed.suggestedSeverity) ? parsed.suggestedSeverity : (severity || 'HIGH'),
      recommendedUnit: parsed.recommendedUnit || fallbackUnit,
      spamRiskScore: typeof parsed.spamRiskScore === 'number' ? parsed.spamRiskScore : 5
    };
  } catch (err) {
    console.error('[GEMINI TRIAGE ERROR]', err.message);
    return fallback;
  }
}

export async function generateRoleScopedChat({ role, userMessage, contextData }) {
  const roleUpper = (role || 'CITIZEN').toUpperCase();

  const systemInstructions = {
    CITIZEN: "You are RapidResQ AI Citizen Assistant. Answer concisely and clearly in plain text. Guide citizens on emergency preparedness and tracking status grounded ONLY in the provided contextData. NEVER reveal internal audit logs or other citizens' reports. If this is a life-threatening crisis, remind them to dial official emergency services (Dial 112).",
    DISPATCHER: "You are RapidResQ AI Dispatcher Copilot. Assist central command by summarizing active incidents, suggesting resource deployment priorities, and evaluating duplicate clusters based strictly on the provided contextData.",
    RESPONDER: "You are RapidResQ AI Field Operations Advisor. Provide tactical safety guidance, situational advice, and procedural next steps for the assigned response unit based strictly on the provided contextData.",
    ADMIN: "You are RapidResQ AI Governance & Security Auditor. Answer questions regarding system workload, unit availability, 403 access control violations, and audit log integrity based strictly on the provided contextData."
  };

  const sysInstruction = systemInstructions[roleUpper] || systemInstructions.CITIZEN;

  if (!ai) {
    return `[AI Offline - GEMINI_API_KEY not configured] Based on your query "${userMessage}", please review the operational context or contact support.`;
  }

  try {
    const prompt = `System Role: ${roleUpper}
System Instructions: ${sysInstruction}

Context Data (JSON):
${JSON.stringify(contextData, null, 2)}

User Query: ${userMessage}
Provide a direct, helpful, and concise response grounded ONLY in the context data above.`;

    const response = await ai.models.generateContent({
      model: MODEL,
      contents: prompt
    });

    return response.text || 'Unable to generate response.';
  } catch (err) {
    console.error('[GEMINI CHAT ERROR]', err.message);
    return 'AI service temporarily unavailable. Please refer to dashboard telemetry.';
  }
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
