import React, { useState, useEffect } from 'react';
import axios from 'axios';

interface Capability {
  capabilityId: string;
  capabilityName: string;
  description: string;
}

interface Category {
  id: string;
  name: string;
  description: string;
}

interface FormData {
  name: string;
  description: string;
  categoryId: string;
  endpointUrl: string;
  icon: string;
  color: string;
  developerName: string;
  developerEmail: string;
  developerOrganization: string;
  capabilities: Capability[];
  exampleQueries: string[];
}

const AgentOnboarding: React.FC = () => {
  const [categories, setCategories] = useState<Category[]>([]);
  const [formData, setFormData] = useState<FormData>({
    name: '',
    description: '',
    categoryId: '',
    endpointUrl: '',
    icon: '🤖',
    color: '#6366f1',
    developerName: '',
    developerEmail: '',
    developerOrganization: '',
    capabilities: [],
    exampleQueries: ['']
  });
  
  const [currentStep, setCurrentStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const API_URL = import.meta.env.VITE_AGENT_REGISTRY_URL || 'http://localhost:8090';
      const response = await axios.get(`${API_URL}/api/categories`);
      setCategories(response.data);
    } catch (error) {
      console.error('Failed to fetch categories:', error);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const addCapability = () => {
    setFormData(prev => ({
      ...prev,
      capabilities: [...prev.capabilities, { capabilityId: '', capabilityName: '', description: '' }]
    }));
  };

  const removeCapability = (index: number) => {
    setFormData(prev => ({
      ...prev,
      capabilities: prev.capabilities.filter((_, i) => i !== index)
    }));
  };

  const updateCapability = (index: number, field: keyof Capability, value: string) => {
    setFormData(prev => ({
      ...prev,
      capabilities: prev.capabilities.map((cap, i) => 
        i === index ? { ...cap, [field]: value } : cap
      )
    }));
  };

  const addExampleQuery = () => {
    setFormData(prev => ({
      ...prev,
      exampleQueries: [...prev.exampleQueries, '']
    }));
  };

  const removeExampleQuery = (index: number) => {
    setFormData(prev => ({
      ...prev,
      exampleQueries: prev.exampleQueries.filter((_, i) => i !== index)
    }));
  };

  const updateExampleQuery = (index: number, value: string) => {
    setFormData(prev => ({
      ...prev,
      exampleQueries: prev.exampleQueries.map((query, i) => 
        i === index ? value : query
      )
    }));
  };

  const validateStep = (step: number): boolean => {
    switch (step) {
      case 1:
        return !!(formData.name && formData.description && formData.categoryId);
      case 2:
        return !!(formData.endpointUrl && formData.icon && formData.color);
      case 3:
        return !!(formData.developerName && formData.developerEmail);
      case 4:
        return formData.capabilities.length > 0 && 
               formData.capabilities.every(cap => cap.capabilityId && cap.capabilityName);
      case 5:
        return formData.exampleQueries.filter(q => q.trim()).length > 0;
      default:
        return true;
    }
  };

  const nextStep = () => {
    if (validateStep(currentStep)) {
      setMessage(null);
      setCurrentStep(prev => prev + 1);
    } else {
      setMessage({ type: 'error', text: 'Please fill in all required fields' });
    }
  };

  const prevStep = () => {
    setMessage(null);
    setCurrentStep(prev => prev - 1);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateStep(5)) {
      setMessage({ type: 'error', text: 'Please fill in all required fields' });
      return;
    }

    setLoading(true);
    setMessage(null);

    try {
      const API_URL = import.meta.env.VITE_AGENT_REGISTRY_URL || 'http://localhost:8090';
      
      // Filter out empty example queries
      const cleanedFormData = {
        ...formData,
        exampleQueries: formData.exampleQueries.filter(q => q.trim())
      };

      const response = await axios.post(`${API_URL}/api/agents/register`, cleanedFormData);
      
      if (response.data.success) {
        setMessage({ 
          type: 'success', 
          text: `Success! Your agent "${formData.name}" has been registered with ID: ${response.data.agentId}. It will be reviewed and activated soon.` 
        });
        
        // Reset form after successful submission
        setTimeout(() => {
          window.location.href = '/';
        }, 3000);
      } else {
        setMessage({ type: 'error', text: response.data.message || 'Failed to register agent' });
      }
    } catch (error: any) {
      setMessage({ 
        type: 'error', 
        text: error.response?.data?.message || 'Failed to register agent. Please try again.' 
      });
    } finally {
      setLoading(false);
    }
  };

  const renderStep = () => {
    switch (currentStep) {
      case 1:
        return (
          <div className="space-y-4">
            <h2 className="text-2xl font-bold text-gray-900">Basic Information</h2>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Agent Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="e.g., Email Processing Agent"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Description <span className="text-red-500">*</span>
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                rows={4}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="Describe what your agent does..."
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Category <span className="text-red-500">*</span>
              </label>
              <select
                name="categoryId"
                value={formData.categoryId}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                required
              >
                <option value="">Select a category</option>
                {categories.map(cat => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name} - {cat.description}
                  </option>
                ))}
              </select>
            </div>
          </div>
        );

      case 2:
        return (
          <div className="space-y-4">
            <h2 className="text-2xl font-bold text-gray-900">Technical Details</h2>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Endpoint URL <span className="text-red-500">*</span>
              </label>
              <input
                type="url"
                name="endpointUrl"
                value={formData.endpointUrl}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="http://your-agent:8080"
                required
              />
              <p className="text-sm text-gray-500 mt-1">Must start with http:// or https://</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Icon (Emoji) <span className="text-red-500">*</span>
              </label>
              <div className="flex items-center gap-4">
                <input
                  type="text"
                  name="icon"
                  value={formData.icon}
                  onChange={handleInputChange}
                  className="w-24 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent text-2xl text-center"
                  placeholder="🤖"
                  maxLength={2}
                  required
                />
                <span className="text-4xl">{formData.icon}</span>
                <div className="text-sm text-gray-500">
                  <p>Choose an emoji to represent your agent</p>
                  <p className="text-xs">Popular: 🤖 📧 📊 🔔 💬 🔍 📅</p>
                </div>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Brand Color <span className="text-red-500">*</span>
              </label>
              <div className="flex items-center gap-4">
                <input
                  type="color"
                  name="color"
                  value={formData.color}
                  onChange={handleInputChange}
                  className="w-16 h-12 border border-gray-300 rounded-lg cursor-pointer"
                  required
                />
                <input
                  type="text"
                  name="color"
                  value={formData.color}
                  onChange={handleInputChange}
                  className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent font-mono"
                  placeholder="#6366f1"
                  pattern="^#[0-9A-Fa-f]{6}$"
                />
                <div 
                  className="w-12 h-12 rounded-lg border border-gray-300" 
                  style={{ backgroundColor: formData.color }}
                />
              </div>
            </div>
          </div>
        );

      case 3:
        return (
          <div className="space-y-4">
            <h2 className="text-2xl font-bold text-gray-900">Developer Contact</h2>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Your Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="developerName"
                value={formData.developerName}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="John Doe"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Email Address <span className="text-red-500">*</span>
              </label>
              <input
                type="email"
                name="developerEmail"
                value={formData.developerEmail}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="john.doe@example.com"
                required
              />
              <p className="text-sm text-gray-500 mt-1">We'll contact you for updates about your agent</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Organization (Optional)
              </label>
              <input
                type="text"
                name="developerOrganization"
                value={formData.developerOrganization}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                placeholder="Tech Innovators Inc"
              />
            </div>
          </div>
        );

      case 4:
        return (
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <h2 className="text-2xl font-bold text-gray-900">Capabilities</h2>
              <button
                type="button"
                onClick={addCapability}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                + Add Capability
              </button>
            </div>

            {formData.capabilities.length === 0 ? (
              <div className="text-center py-8 bg-gray-50 rounded-lg border-2 border-dashed border-gray-300">
                <p className="text-gray-600 mb-2">No capabilities added yet</p>
                <button
                  type="button"
                  onClick={addCapability}
                  className="text-indigo-600 hover:text-indigo-700 font-medium"
                >
                  Add your first capability
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                {formData.capabilities.map((cap, index) => (
                  <div key={index} className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                    <div className="flex justify-between items-start mb-3">
                      <h3 className="font-medium text-gray-900">Capability {index + 1}</h3>
                      <button
                        type="button"
                        onClick={() => removeCapability(index)}
                        className="text-red-600 hover:text-red-700"
                      >
                        Remove
                      </button>
                    </div>
                    
                    <div className="space-y-3">
                      <input
                        type="text"
                        value={cap.capabilityId}
                        onChange={(e) => updateCapability(index, 'capabilityId', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                        placeholder="Capability ID (e.g., email-read)"
                        required
                      />
                      
                      <input
                        type="text"
                        value={cap.capabilityName}
                        onChange={(e) => updateCapability(index, 'capabilityName', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                        placeholder="Capability Name (e.g., Read Emails)"
                        required
                      />
                      
                      <textarea
                        value={cap.description}
                        onChange={(e) => updateCapability(index, 'description', e.target.value)}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                        placeholder="Description"
                        rows={2}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        );

      case 5:
        return (
          <div className="space-y-4">
            <div className="flex justify-between items-center">
              <h2 className="text-2xl font-bold text-gray-900">Example Queries</h2>
              <button
                type="button"
                onClick={addExampleQuery}
                className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                + Add Query
              </button>
            </div>

            <p className="text-gray-600">
              Provide example queries that users can ask your agent. These help users understand what your agent can do.
            </p>

            <div className="space-y-3">
              {formData.exampleQueries.map((query, index) => (
                <div key={index} className="flex items-center gap-3">
                  <span className="text-gray-500 font-medium">{index + 1}.</span>
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => updateExampleQuery(index, e.target.value)}
                    className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                    placeholder="e.g., Show me unread emails from last week"
                  />
                  {formData.exampleQueries.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeExampleQuery(index)}
                      className="text-red-600 hover:text-red-700 px-3 py-2"
                    >
                      Remove
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-indigo-50 via-white to-purple-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="bg-white rounded-2xl shadow-xl p-8">
          {/* Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Register Your Agent</h1>
            <p className="text-gray-600">
              Fill out the form below to submit your agent to the marketplace
            </p>
          </div>

          {/* Progress Bar */}
          <div className="mb-8">
            <div className="flex justify-between items-center mb-2">
              {[1, 2, 3, 4, 5].map(step => (
                <div
                  key={step}
                  className={`flex items-center justify-center w-10 h-10 rounded-full font-semibold ${
                    step <= currentStep
                      ? 'bg-indigo-600 text-white'
                      : 'bg-gray-200 text-gray-600'
                  }`}
                >
                  {step}
                </div>
              ))}
            </div>
            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
              <div
                className="h-full bg-indigo-600 transition-all duration-300"
                style={{ width: `${(currentStep / 5) * 100}%` }}
              />
            </div>
            <div className="flex justify-between mt-2 text-sm text-gray-600">
              <span>Basic</span>
              <span>Technical</span>
              <span>Contact</span>
              <span>Capabilities</span>
              <span>Examples</span>
            </div>
          </div>

          {/* Message */}
          {message && (
            <div
              className={`mb-6 p-4 rounded-lg ${
                message.type === 'success'
                  ? 'bg-green-50 border border-green-200 text-green-800'
                  : 'bg-red-50 border border-red-200 text-red-800'
              }`}
            >
              {message.text}
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit}>
            {renderStep()}

            {/* Navigation Buttons */}
            <div className="flex justify-between mt-8 pt-6 border-t border-gray-200">
              <button
                type="button"
                onClick={prevStep}
                disabled={currentStep === 1}
                className="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>

              {currentStep < 5 ? (
                <button
                  type="button"
                  onClick={nextStep}
                  className="px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  Next Step
                </button>
              ) : (
                <button
                  type="submit"
                  disabled={loading}
                  className="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {loading ? 'Submitting...' : 'Submit Agent'}
                </button>
              )}
            </div>
          </form>

          {/* Cancel Button */}
          <div className="mt-4 text-center">
            <button
              type="button"
              onClick={() => window.location.href = '/'}
              className="text-gray-600 hover:text-gray-800"
            >
              Cancel and return to home
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AgentOnboarding;
