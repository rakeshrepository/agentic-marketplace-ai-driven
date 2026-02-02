import React from 'react';
import { Agent } from '../types';

interface AgentCardProps {
  agent: Agent;
  onSelect: (agent: Agent) => void;
  isSelected: boolean;
}

const iconMap: Record<string, string> = {
  kafka: '📊',
  server: '🖥️',
  code: '💻',
  puzzle: '🧩',
  default: '🤖',
};

export const AgentCard: React.FC<AgentCardProps> = ({ agent, onSelect, isSelected }) => {
  const icon = iconMap[agent.icon] || iconMap.default;

  return (
    <div
      onClick={() => onSelect(agent)}
      className={`group cursor-pointer p-5 rounded-xl border transition-all duration-300 hover:scale-[1.02] ${
        isSelected
          ? 'border-purple-500/50 bg-gradient-to-br from-purple-500/20 to-pink-500/20 backdrop-blur-sm shadow-lg shadow-purple-500/20'
          : 'border-white/10 bg-white/5 backdrop-blur-sm hover:border-purple-500/30 hover:bg-white/10'
      }`}
    >
      <div className="flex items-start gap-4">
        <div className={`text-4xl transition-transform duration-300 ${isSelected ? 'scale-110' : 'group-hover:scale-110'}`}>
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="font-bold text-lg text-white truncate">{agent.name}</h3>
          <p className="text-sm text-purple-200/70 mt-1 line-clamp-2">{agent.description}</p>
          <div className="flex flex-wrap gap-1.5 mt-3">
            {agent.capabilities.slice(0, 3).map((cap) => (
              <span
                key={cap}
                className="px-2.5 py-1 bg-purple-500/20 border border-purple-500/30 text-purple-200 text-xs rounded-lg font-medium"
              >
                {cap}
              </span>
            ))}
            {agent.capabilities.length > 3 && (
              <span className="px-2.5 py-1 bg-purple-500/10 border border-purple-500/20 text-purple-300 text-xs rounded-lg">
                +{agent.capabilities.length - 3}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
