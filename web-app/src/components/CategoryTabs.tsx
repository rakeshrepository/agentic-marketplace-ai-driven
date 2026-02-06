import React from 'react';
import { Category } from '../types';

interface CategoryTabsProps {
  categories: Category[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
  agentCounts: Record<string, number>;
}

export const CategoryTabs: React.FC<CategoryTabsProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
  agentCounts,
}) => {
  const totalCount = Object.values(agentCounts).reduce((sum, count) => sum + count, 0);

  return (
    <div className="sticky top-0 z-10 backdrop-blur-md bg-slate-900/95 border-b border-white/10 shadow-xl">
      <div className="max-w-7xl mx-auto px-6">
        <div className="flex items-center gap-3 overflow-x-auto py-4 scrollbar-thin scrollbar-thumb-blue-500/50 scrollbar-track-transparent">
          {/* All Category */}
          <button
            onClick={() => onSelectCategory('all')}
            className={`
              flex items-center gap-2 px-5 py-3 rounded-xl font-semibold whitespace-nowrap transition-all duration-300 transform hover:scale-105
              ${selectedCategory === 'all'
                ? 'bg-gradient-to-r from-blue-600 to-cyan-600 text-white shadow-lg shadow-blue-500/50'
                : 'bg-white/10 text-blue-200 hover:bg-white/15 hover:text-white border border-white/10'
              }
            `}
          >
            <span className="text-xl">🌟</span>
            <span>All Agents</span>
            <span className={`
              text-xs px-2.5 py-1 rounded-full font-bold
              ${selectedCategory === 'all'
                ? 'bg-white/25 text-white'
                : 'bg-blue-500/30 text-blue-200'
              }
            `}>
              {totalCount}
            </span>
          </button>

          {/* Category Tabs */}
          {categories.map((category) => {
            const count = agentCounts[category.id] || 0;
            const isSelected = selectedCategory === category.id;

            return (
              <button
                key={category.id}
                onClick={() => onSelectCategory(category.id)}
                className={`
                  flex items-center gap-2 px-5 py-3 rounded-xl font-semibold whitespace-nowrap transition-all duration-300 transform hover:scale-105
                  ${isSelected
                    ? 'bg-gradient-to-r from-blue-600 to-cyan-600 text-white shadow-lg shadow-blue-500/50'
                    : 'bg-white/10 text-blue-200 hover:bg-white/15 hover:text-white border border-white/10'
                  }
                `}
              >
                <span className="text-xl">{getCategoryIcon(category.id)}</span>
                <span>{cleanCategoryName(category.name)}</span>
                <span className={`
                  text-xs px-2.5 py-1 rounded-full font-bold
                  ${isSelected
                    ? 'bg-white/25 text-white'
                    : 'bg-blue-500/30 text-blue-200'
                  }
                `}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};

// Use the same category icons as in AgentList
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

function getCategoryIcon(categoryId: string): string {
  return categoryIconMap[categoryId] || categoryIconMap.default;
}

function cleanCategoryName(name: string): string {
  // Remove emoji and extra spaces from the start of the name
  return name.replace(/^[^\w\s]+\s*/, '').trim();
}
