import axios from 'axios';
import { AgentRequest, AgentResponse } from '../types';

// In Docker environment, this will be proxied through nginx
// In development, Vite proxy handles it
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

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
      `${API_BASE_URL}${agentEndpoint}/query`,
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
