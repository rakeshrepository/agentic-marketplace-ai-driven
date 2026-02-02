import { AgentRegistry } from '../types';

// This mirrors the agent-list.json from the SDK
// In production, this could be fetched from an API endpoint
export const agentRegistry: AgentRegistry = {
  agents: [
    {
      id: 'topic-management-agent',
      name: 'Kafka Topic Management Agent',
      description: 'Manage Kafka topics using natural language. Create, list, delete, and describe topics in your Kafka cluster.',
      category: 'infrastructure',
      endpoint: '/api/agent',
      icon: 'kafka',
      capabilities: ['create-topic', 'list-topics', 'delete-topic', 'describe-topic'],
    },
  ],
  categories: [
    {
      id: 'infrastructure',
      name: 'Infrastructure',
      description: 'Agents for managing infrastructure components like Kafka, queues, databases, etc.',
      icon: 'server',
    },
    {
      id: 'development',
      name: 'Development',
      description: 'Agents for development tasks like code generation, testing, documentation, etc.',
      icon: 'code',
    },
    {
      id: 'custom',
      name: 'Custom',
      description: 'Custom business-specific agents built by your team.',
      icon: 'puzzle',
    },
  ],
};
