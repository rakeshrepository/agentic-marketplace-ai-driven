import React from 'react';
import { Agent, Category } from '../types';

interface AgentListProps {
  agents: Agent[];
  categories: Category[];
  selectedAgent: Agent | null;
  onSelectAgent: (agent: Agent) => void;
  searchQuery: string;
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

const iconMap: Record<string, string> = {
  kafka: '📊',
  database: '🗄️',
  cloud: '☁️',
  storage: '💾',
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
            <div className="flex items-center gap-4 pb-2 border-b border-white/10">
              <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500/20 to-cyan-500/20 border border-blue-400/30">
                <span className="text-3xl">{categoryIcon}</span>
              </div>
              <div className="flex-1">
                <h2 className="text-2xl font-bold bg-gradient-to-r from-blue-400 via-cyan-400 to-indigo-400 bg-clip-text text-transparent">
                  {category.name}
                </h2>
                <p className="text-sm text-blue-200/70 mt-1">{category.description}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-blue-300 font-semibold px-3 py-2 bg-gradient-to-r from-blue-500/20 to-cyan-500/20 rounded-lg border border-blue-400/30">
                  {displayAgents.length} of {categoryAgents.length} {categoryAgents.length === 1 ? 'agent' : 'agents'}
                </span>
              </div>
            </div>

            {/* Flashcards Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {displayAgents.map((agent) => {
                const agentIcon = iconMap[agent.icon] || iconMap.default;
                const isComingSoon = agent.status === 'coming-soon';
                const isDisabled = isComingSoon;

                return (
                  <div
                    key={agent.id}
                    onClick={() => !isDisabled && onSelectAgent(agent)}
                    className={`group relative p-6 rounded-2xl transition-all duration-300 ${
                      isDisabled 
                        ? 'cursor-not-allowed opacity-60 bg-gradient-to-br from-slate-800/50 to-slate-900/50 border border-slate-700/50' 
                        : 'cursor-pointer hover:scale-[1.03] hover:shadow-2xl'
                    } ${
                      selectedAgent?.id === agent.id
                        ? 'bg-gradient-to-br from-blue-600/30 via-cyan-600/20 to-indigo-600/30 border-2 border-blue-400/60 shadow-xl shadow-blue-500/40 ring-2 ring-blue-400/30'
                        : 'bg-gradient-to-br from-slate-800/80 to-slate-900/80 border-2 border-white/10 hover:border-blue-400/40 hover:from-slate-800/90 hover:to-slate-900/90 backdrop-blur-sm'
                    }`}
                  >
                    {/* Coming Soon Badge */}
                    {isComingSoon && (
                      <div className="absolute top-3 right-3 px-3 py-1.5 bg-gradient-to-r from-amber-500/30 to-orange-500/30 border border-amber-400/50 text-amber-200 text-xs rounded-lg font-bold shadow-lg backdrop-blur-sm">
                        Coming Soon
                      </div>
                    )}

                    {/* Icon with Background */}
                    <div className="flex justify-center mb-4">
                      <div className={`relative flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-blue-500/20 to-cyan-500/20 border border-blue-400/30 transition-all duration-300 ${
                        !isDisabled && 'group-hover:scale-110 group-hover:rotate-3 group-hover:shadow-lg group-hover:shadow-blue-500/30'
                      }`}>
                        <span className="text-5xl">{agentIcon}</span>
                        {!isDisabled && (
                          <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-blue-400/0 to-cyan-400/0 group-hover:from-blue-400/10 group-hover:to-cyan-400/10 transition-all duration-300" />
                        )}
                      </div>
                    </div>

                    {/* Agent Name */}
                    <h3 className="font-bold text-center text-white mb-2 line-clamp-1 text-lg">
                      {agent.name}
                    </h3>

                    {/* Description */}
                    <p className="text-sm text-blue-200/80 text-center line-clamp-2 mb-4 min-h-[2.5rem]">
                      {agent.description}
                    </p>

                    {/* Capabilities Count */}
                    <div className="flex items-center justify-center gap-2 pt-3 border-t border-white/10">
                      <div className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-blue-500/20 to-cyan-500/20 rounded-lg border border-blue-400/30">
                        <span className="text-sm">⚡</span>
                        <span className="text-xs font-semibold text-blue-200">{agent.capabilities.length} capabilities</span>
                      </div>
                    </div>

                    {/* Hover Effect Border */}
                    {!isDisabled && (
                      <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-blue-500/0 to-cyan-500/0 group-hover:from-blue-500/5 group-hover:to-cyan-500/5 transition-all duration-300 pointer-events-none" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
      {filteredAgents.length === 0 && (
        <div className="text-center py-16">
          <div className="inline-block p-6 rounded-2xl bg-gradient-to-br from-slate-800/80 to-slate-900/80 border-2 border-white/10 mb-4">
            <div className="text-6xl mb-4">🔍</div>
          </div>
          <h3 className="text-xl font-bold text-white mb-2">No Agents Found</h3>
          <p className="text-blue-200/70">Try adjusting your search query: "{searchQuery}"</p>
        </div>
      )}
    </div>
  );
};
