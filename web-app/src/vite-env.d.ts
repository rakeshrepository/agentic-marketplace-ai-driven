/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_AGENT_REGISTRY_URL: string;
  readonly VITE_KAFKA_MCP_URL: string;
  readonly VITE_DATABASE_MCP_URL: string;
  readonly VITE_OLLAMA_URL: string;
  readonly VITE_OLLAMA_MODEL: string;
  readonly VITE_OLLAMA_TEMPERATURE: string;
  readonly VITE_OLLAMA_NUM_PREDICT: string;
  readonly VITE_MCP_REQUEST_ID_START: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
