import React, { useState } from 'react';
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
  infrastructure: '⚡',
  devops: '�',
  'developer-tools': '🚀',
  'cicd-automation': '🔄',
  'data-analytics': '📊',
  'security-monitoring': '🛡️',
  'ai-intelligent': '�',
  'integration-hub': '🌐',
  'custom-solutions': '🧩',
  default: '📁',
};

export const AgentList: React.FC<AgentListProps> = ({
  agents,
  categories,
  selectedAgent,
  onSelectAgent,
  searchQuery,
}) => {
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(categories.map(c => c.id)) // All categories expanded by default
  );

  const toggleCategory = (categoryId: string) => {
    setExpandedCategories(prev => {
      const newSet = new Set(prev);
      if (newSet.has(categoryId)) {
        newSet.delete(categoryId);
      } else {
        newSet.add(categoryId);
      }
      return newSet;
    });
  };

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
    <div className="space-y-4">
      {categories.map((category) => {
        const categoryAgents = agentsByCategory[category.id] || [];
        if (categoryAgents.length === 0) return null;

        const icon = categoryIconMap[category.id] || categoryIconMap.default;
        const isExpanded = expandedCategories.has(category.id);

        return (
          <div key={category.id} className="border border-white/10 rounded-xl bg-white/5 backdrop-blur-sm overflow-hidden">
            <div 
              className="flex items-center justify-between p-4 cursor-pointer hover:bg-white/10 transition-colors"
              onClick={() => toggleCategory(category.id)}
            >
              <div className="flex items-center gap-3 flex-1">
                <span className="text-2xl">{icon}</span>
                <h2 className="text-lg font-bold bg-gradient-to-r from-blue-300 to-cyan-300 bg-clip-text text-transparent">
                  {category.name}
                </h2>
                <span className="text-xs text-blue-200/60 font-medium px-2 py-1 bg-blue-500/20 rounded-md">
                  {categoryAgents.length}
                </span>
              </div>
              <div className={`text-blue-300 transition-transform duration-300 ${isExpanded ? 'rotate-180' : ''}`}>
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </div>
            
            {isExpanded && (
              <div className="px-4 pb-4 space-y-3">
                <p className="text-xs text-blue-200/60 mb-3">{category.description}</p>
                {categoryAgents.map((agent) => (
                  <AgentCard
                    key={agent.id}
                    agent={agent}
                    onSelect={onSelectAgent}
                    isSelected={selectedAgent?.id === agent.id}
                  />
                ))}
              </div>
            )}
          </div>
        );
      })}
      {filteredAgents.length === 0 && (
        <div className="text-center py-12">
          <div className="text-5xl mb-4">🔍</div>
          <p className="text-blue-200/70">No agents found matching "{searchQuery}"</p>
        </div>
      )}
    </div>
  );
};
