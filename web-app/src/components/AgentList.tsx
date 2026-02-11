import React from 'react';
import { Agent, Category } from '../types';
import { AgentCard } from './AgentCard';

interface AgentListProps {
  agents: Agent[];
  categories: Category[];
  onSelectAgent: (agent: Agent) => void;
  searchQuery: string;
  onDeleteAgent?: (agentId: string) => void;
}

const categoryIconMap: Record<string, string> = {
  infrastructure: '⚡',
  devops: '🔧',
  'developer-tools': '🚀',
  'cicd-automation': '🔄',
  'data-analytics': '📊',
  'security-monitoring': '🛡️',
  'ai-intelligent': '💬',
  'integration-hub': '🌐',
  'custom-solutions': '🧩',
  default: '📁',
};

export const AgentList: React.FC<AgentListProps> = ({
  agents,
  categories,
  onSelectAgent,
  searchQuery,
  onDeleteAgent,
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
    <div className="space-y-10 pb-8">
      {categories.map((category) => {
        const categoryAgents = agentsByCategory[category.id] || [];
        if (categoryAgents.length === 0) return null;

        // Show only first 3 agents
        const displayAgents = categoryAgents.slice(0, 3);
        const categoryIcon = categoryIconMap[category.id] || categoryIconMap.default;

        return (
          <div key={category.id} className="space-y-5">
            {/* Category Header */}
            <div className="flex items-center gap-4 pb-2 border-b border-purple-500/20">
              <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-indigo-600/30 via-purple-600/30 to-pink-600/30 border border-purple-400/40 shadow-lg shadow-purple-500/30">
                <span className="text-3xl">{categoryIcon}</span>
              </div>
              <div className="flex-1">
                <h2 className="text-2xl font-bold bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400 bg-clip-text text-transparent drop-shadow-lg">
                  {category.name}
                </h2>
                <p className="text-sm text-slate-300/80 mt-1 font-light">{category.description}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-purple-300 font-semibold px-3 py-2 bg-gradient-to-r from-indigo-600/20 via-purple-600/20 to-pink-600/20 rounded-lg border border-purple-400/40 shadow-md shadow-purple-500/20">
                  {displayAgents.length} of {categoryAgents.length} {categoryAgents.length === 1 ? 'agent' : 'agents'}
                </span>
              </div>
            </div>

            {/* Flashcards Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {displayAgents.map((agent) => (
                <AgentCard
                  key={agent.id}
                  agent={agent}
                  onSelect={onSelectAgent}
                  isSelected={false}
                  onDelete={agent.status !== 'coming-soon' ? onDeleteAgent : undefined}
                />
              ))}
            </div>
          </div>
        );
      })}
      {filteredAgents.length === 0 && (
        <div className="text-center py-16">
          <div className="inline-block p-6 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950/90 border-2 border-slate-700/50 mb-4 shadow-xl">
            <div className="text-6xl mb-4">🔍</div>
          </div>
          <h3 className="text-xl font-bold text-white mb-2">No Agents Found</h3>
          <p className="text-slate-300/70 font-light">Try adjusting your search query: "{searchQuery}"</p>
        </div>
      )}
    </div>
  );
};
