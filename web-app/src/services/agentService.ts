import axios from 'axios';
import { Agent, AgentRequest, AgentResponse } from '../types';

// In Docker environment, this will be proxied through nginx
// In development, Vite proxy handles it
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const AGENT_REGISTRY_URL = import.meta.env.VITE_AGENT_REGISTRY_URL || '';

// Agent Registry API
export const getAllAgents = async (): Promise<Agent[]> => {
  try {
    const response = await axios.get(`${AGENT_REGISTRY_URL}/api/agents`);
    return response.data.map((agent: any) => transformAgent(agent));
  } catch (error) {
    console.error('Error fetching agents:', error);
    return [];
  }
};

export const getAgentById = async (id: string): Promise<Agent | null> => {
  try {
    const response = await axios.get(`${AGENT_REGISTRY_URL}/api/agents/${id}`);
    return transformAgent(response.data);
  } catch (error) {
    console.error(`Error fetching agent ${id}:`, error);
    return null;
  }
};

const transformAgent = (apiAgent: any): Agent => {
  return {
    id: apiAgent.id,
    name: apiAgent.name,
    description: apiAgent.description || '',
    category: apiAgent.categoryId,
    endpoint: apiAgent.endpointUrl || '/api/agent',
    icon: mapIconToType(apiAgent.icon),
    capabilities: apiAgent.capabilities?.map((c: any) => c.capabilityId) || [],
    status: apiAgent.status,
    color: apiAgent.color,
    exampleQueries: apiAgent.exampleQueries?.map((q: any) => q.query) || [],
  };
};

const mapIconToType = (icon: string): string => {
  // Map emoji icons to icon types
  const iconMap: Record<string, string> = {
    '🔄': 'kafka',
    '🗄️': 'database',
    '☁️': 'cloud',
    '📦': 'storage',
    '💻': 'code',
    '📊': 'chart',
    '🤖': 'ai',
    '🔒': 'security',
    '💬': 'communication',
    '📧': 'email',
  };
  return iconMap[icon] || 'default';
};

// Agent Query API
export const sendQuery = async (
  agentEndpoint: string,
  query: string
): Promise<AgentResponse> => {
  try {
    const request: AgentRequest = {
      query,
      sessionId: crypto.randomUUID(),
    };

    const response = await axios.post<AgentResponse>(
      `${API_BASE_URL}${agentEndpoint}/api/agent/query`,
      request,
      {
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      return {
        success: false,
        error: error.response?.data?.error || error.message,
      };
    }
    return {
      success: false,
      error: 'An unexpected error occurred',
    };
  }
};

export const checkAgentHealth = async (agentEndpoint: string): Promise<boolean> => {
  try {
    const response = await axios.get(`${API_BASE_URL}${agentEndpoint}/health`);
    return response.data?.success === true;
  } catch {
    return false;
  }
};
