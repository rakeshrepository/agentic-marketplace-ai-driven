import React, { useState } from 'react';
import { Agent } from '../types';

interface AgentCardProps {
  agent: Agent;
  onSelect: (agent: Agent) => void;
  isSelected: boolean;
  onDelete?: (agentId: string) => void;
}

const iconMap: Record<string, string> = {
  // Infrastructure & DevOps
  kafka: '📊',
  database: '🗄️',
  cloud: '☁️',
  storage: '💾',
  redis: '⚡',
  mongodb: '🍃',
  postgres: '🐘',
  mysql: '🐬',
  server: '🖥️',
  container: '📦',
  docker: '🐳',
  kubernetes: '☸️',
  
  // Development Tools
  code: '💻',
  git: '🔀',
  github: '🐙',
  gitlab: '🦊',
  vscode: '📝',
  terminal: '⌨️',
  compiler: '⚙️',
  
  // Documentation & Learning
  book: '📚',
  docs: '📖',
  wiki: '📄',
  readme: '📋',
  tutorial: '🎓',
  
  // Testing & Quality
  test: '🧪',
  debug: '🐛',
  quality: '✅',
  security: '🔒',
  shield: '🛡️',
  
  // CI/CD & Automation
  pipeline: '🔄',
  automation: '⚡',
  jenkins: '👨‍🔧',
  cicd: '🔁',
  deploy: '🚀',
  
  // Monitoring & Analytics
  chart: '📈',
  analytics: '📊',
  metrics: '📉',
  monitor: '👁️',
  alert: '🔔',
  log: '📝',
  
  // Communication & Collaboration
  support: '💬',
  chat: '💭',
  email: '📧',
  notification: '🔔',
  slack: '💬',
  
  // AI & Intelligence
  ai: '🤖',
  robot: '🤖',
  brain: '🧠',
  magic: '✨',
  spark: '⚡',
  neural: '�️',
  
  // Data & Integration
  puzzle: '🧩',
  integration: '🔗',
  api: '🔌',
  webhook: '🪝',
  data: '💽',
  sync: '🔄',
  
  // Miscellaneous
  tool: '�',
  config: '🔧',
  settings: '⚙️',
  package: '📦',
  plugin: '🧩',
  extension: '🎨',
  default: '🤖',
};

export const AgentCard: React.FC<AgentCardProps> = ({ agent, onSelect, isSelected, onDelete }) => {
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const icon = iconMap[agent.icon] || iconMap.default;
  const isComingSoon = agent.status === 'coming-soon';
  const isPending = agent.status === 'pending';
  const isDisabled = isComingSoon;
  const canDelete = !isComingSoon && onDelete; // Can delete active or pending agents

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (showDeleteConfirm && onDelete) {
      onDelete(agent.id);
    } else {
      setShowDeleteConfirm(true);
      setTimeout(() => setShowDeleteConfirm(false), 3000);
    }
  };

  const handleCancelDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowDeleteConfirm(false);
  };

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
            {isPending && (
              <span className="px-2 py-0.5 bg-gradient-to-r from-purple-500/20 to-pink-500/20 border border-purple-500/40 text-purple-300 text-xs rounded-md font-semibold whitespace-nowrap">
                Pending Review
              </span>
            )}
            {agent.status === 'beta' && (
              <span className="px-2 py-0.5 bg-gradient-to-r from-blue-500/20 to-cyan-500/20 border border-blue-500/40 text-blue-300 text-xs rounded-md font-semibold whitespace-nowrap">
                Beta
              </span>
            )}
            {canDelete && (
              <div className="ml-auto">
                {showDeleteConfirm ? (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={handleDelete}
                      className="px-2 py-1 bg-red-500/20 border border-red-500/40 text-red-300 text-xs rounded-md font-semibold hover:bg-red-500/30 transition-colors"
                    >
                      Confirm
                    </button>
                    <button
                      onClick={handleCancelDelete}
                      className="px-2 py-1 bg-gray-500/20 border border-gray-500/40 text-gray-300 text-xs rounded-md font-semibold hover:bg-gray-500/30 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={handleDelete}
                    className="px-2 py-1 bg-red-500/10 border border-red-500/30 text-red-400 text-xs rounded-md font-semibold hover:bg-red-500/20 transition-colors"
                    title="Delete agent"
                  >
                    Delete
                  </button>
                )}
              </div>
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
