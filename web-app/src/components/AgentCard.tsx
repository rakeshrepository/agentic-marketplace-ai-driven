import React from 'react';
import { Agent } from '../types';

interface AgentCardProps {
  agent: Agent;
  onSelect: (agent: Agent) => void;
  isSelected: boolean;
}

const iconMap: Record<string, string> = {
  kafka: '📊',
  database: '🗄️',
  cloud: '☁️',
  storage: '�',
  code: '💻',
  book: '📚',
  test: '🧪',
  pipeline: '🔄',
  chart: '📈',
  support: '💬',
  server: '🖥️',
  puzzle: '🧩',
  default: '🤖',
};

export const AgentCard: React.FC<AgentCardProps> = ({ agent, onSelect, isSelected }) => {
  const icon = iconMap[agent.icon] || iconMap.default;
  const isComingSoon = agent.status === 'coming-soon';
  const isDisabled = isComingSoon;

  return (
    <div
      onClick={() => !isDisabled && onSelect(agent)}
      className={`group p-5 rounded-xl border transition-all duration-300 ${
        isDisabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:scale-[1.02]'
      } ${
        isSelected
          ? 'border-blue-500/50 bg-gradient-to-br from-blue-500/20 to-cyan-500/20 backdrop-blur-sm shadow-lg shadow-blue-500/20'
          : 'border-white/10 bg-white/5 backdrop-blur-sm hover:border-blue-500/30 hover:bg-white/10'
      }`}
    >
      <div className="flex items-start gap-4">
        <div className={`text-4xl transition-transform duration-300 ${!isDisabled && (isSelected ? 'scale-110' : 'group-hover:scale-110')}`}>
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-lg text-white truncate">{agent.name}</h3>
            {isComingSoon && (
              <span className="px-2 py-0.5 bg-gradient-to-r from-yellow-500/20 to-orange-500/20 border border-yellow-500/40 text-yellow-300 text-xs rounded-md font-semibold whitespace-nowrap">
                Coming Soon
              </span>
            )}
            {agent.status === 'beta' && (
              <span className="px-2 py-0.5 bg-gradient-to-r from-blue-500/20 to-cyan-500/20 border border-blue-500/40 text-blue-300 text-xs rounded-md font-semibold whitespace-nowrap">
                Beta
              </span>
            )}
          </div>
          <p className="text-sm text-blue-200/70 mt-1 line-clamp-2">{agent.description}</p>
          <div className="flex flex-wrap gap-1.5 mt-3">
            {agent.capabilities.slice(0, 3).map((cap) => (
              <span
                key={cap}
                className="px-2.5 py-1 bg-blue-500/20 border border-blue-500/30 text-blue-200 text-xs rounded-lg font-medium"
              >
                {cap}
              </span>
            ))}
            {agent.capabilities.length > 3 && (
              <span className="px-2.5 py-1 bg-blue-500/10 border border-blue-500/20 text-blue-300 text-xs rounded-lg">
                +{agent.capabilities.length - 3}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
