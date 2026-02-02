export interface Agent {
  id: string;
  name: string;
  description: string;
  category: string;
  endpoint: string;
  icon: string;
  capabilities: string[];
  status?: 'active' | 'coming-soon' | 'beta' | 'deprecated';
}

export interface Category {
  id: string;
  name: string;
  description: string;
  icon: string;
}

export interface AgentRegistry {
  agents: Agent[];
  categories: Category[];
}

export interface AgentRequest {
  query: string;
  sessionId?: string;
}

export interface AgentResponse {
  success: boolean;
  message?: string;
  data?: unknown;
  error?: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  data?: unknown;
}
