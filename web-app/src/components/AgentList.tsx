import React from 'react';
import { Agent, Category } from '../types';
import { AgentCard } from './AgentCard';

interface AgentListProps {
  agents: Agent[];
  categories: Category[];
  selectedAgent: Agent | null;
  onSelectAgent: (agent: Agent) => void;
  searchQuery: string;
}

const categoryIconMap: Record<string, string> = {
  infrastructure: '🖥️',
  development: '💻',
  custom: '🧩',
  default: '📁',
};

export const AgentList: React.FC<AgentListProps> = ({
  agents,
  categories,
  selectedAgent,
  onSelectAgent,
  searchQuery,
}) => {
  const filteredAgents = agents.filter(
    (agent) =>
      agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.capabilities.some((cap) =>
        cap.toLowerCase().includes(searchQuery.toLowerCase())
      )
  );

  const agentsByCategory = categories.reduce((acc, category) => {
    acc[category.id] = filteredAgents.filter(
      (agent) => agent.category === category.id
    );
    return acc;
  }, {} as Record<string, Agent[]>);

  return (
    <div className="space-y-6">
      {categories.map((category) => {
        const categoryAgents = agentsByCategory[category.id] || [];
        if (categoryAgents.length === 0) return null;

        const icon = categoryIconMap[category.id] || categoryIconMap.default;

        return (
          <div key={category.id}>
            <div className="flex items-center gap-3 mb-4">
              <span className="text-2xl">{icon}</span>
              <h2 className="text-xl font-bold bg-gradient-to-r from-purple-300 to-pink-300 bg-clip-text text-transparent">
                {category.name}
              </h2>
              <span className="text-sm text-purple-200/60 font-medium px-2.5 py-1 bg-purple-500/20 rounded-lg">
                {categoryAgents.length}
              </span>
            </div>
            <p className="text-sm text-purple-200/70 mb-4">{category.description}</p>
            <div className="grid gap-4">
              {categoryAgents.map((agent) => (
                <AgentCard
                  key={agent.id}
                  agent={agent}
                  onSelect={onSelectAgent}
                  isSelected={selectedAgent?.id === agent.id}
                />
              ))}
            </div>
          </div>
        );
      })}
      {filteredAgents.length === 0 && (
        <div className="text-center py-12">
          <div className="text-5xl mb-4">🔍</div>
          <p className="text-purple-200/70">No agents found matching "{searchQuery}"</p>
        </div>
      )}
    </div>
  );
};
