-- Create categories table
CREATE TABLE categories (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    icon VARCHAR(10),
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create agents table
CREATE TABLE agents (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id VARCHAR(100) REFERENCES categories(id),
    status VARCHAR(50) DEFAULT 'active',
    endpoint_url VARCHAR(500),
    health_check_path VARCHAR(200) DEFAULT '/actuator/health',
    icon VARCHAR(10),
    color VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create agent_capabilities table
CREATE TABLE agent_capabilities (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(100) REFERENCES agents(id) ON DELETE CASCADE,
    capability_id VARCHAR(100) NOT NULL,
    capability_name VARCHAR(255) NOT NULL,
    description TEXT
);

-- Create agent_example_queries table
CREATE TABLE agent_example_queries (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(100) REFERENCES agents(id) ON DELETE CASCADE,
    query TEXT NOT NULL,
    display_order INT DEFAULT 0
);

-- Create indexes
CREATE INDEX idx_agents_category ON agents(category_id);
CREATE INDEX idx_agents_status ON agents(status);
CREATE INDEX idx_capabilities_agent ON agent_capabilities(agent_id);
CREATE INDEX idx_examples_agent ON agent_example_queries(agent_id);

-- Insert default categories
INSERT INTO categories (id, name, description, icon, display_order) VALUES
('infrastructure', 'Infrastructure & DevOps', 'Manage your infrastructure, databases, and messaging systems', '🏗️', 1),
('data-analytics', 'Data & Analytics', 'Process, analyze, and visualize your data', '📊', 2),
('communication', 'Communication', 'Email, messaging, and notification management', '💬', 3);

-- Insert existing agents
INSERT INTO agents (id, name, description, category_id, status, endpoint_url, icon, color) VALUES
('kafka-topic-agent', 'Kafka Topic Management', 'Manage Kafka topics with natural language commands. Create, list, describe, and delete topics effortlessly.', 'infrastructure', 'active', 'http://topic-management-agent:8080', '🔄', '#4F46E5'),
('database-agent', 'Database Management', 'Manage database tables with natural language. Create tables, list schemas, and manage your database structure.', 'infrastructure', 'active', 'http://database-management-agent:8082', '🗄️', '#06B6D4');

-- Insert capabilities for Kafka agent
INSERT INTO agent_capabilities (agent_id, capability_id, capability_name, description) VALUES
('kafka-topic-agent', 'create-topic', 'Create Topic', 'Create a new Kafka topic with specified partitions and replication factor'),
('kafka-topic-agent', 'list-topics', 'List Topics', 'List all available Kafka topics'),
('kafka-topic-agent', 'describe-topic', 'Describe Topic', 'Get detailed information about a specific topic'),
('kafka-topic-agent', 'delete-topic', 'Delete Topic', 'Delete an existing Kafka topic');

-- Insert capabilities for Database agent
INSERT INTO agent_capabilities (agent_id, capability_id, capability_name, description) VALUES
('database-agent', 'create-table', 'Create Table', 'Create a new database table with specified columns'),
('database-agent', 'list-tables', 'List Tables', 'List all tables in the database'),
('database-agent', 'describe-table', 'Describe Table', 'Get schema information for a specific table'),
('database-agent', 'drop-table', 'Drop Table', 'Delete a table from the database');

-- Insert example queries for Kafka agent
INSERT INTO agent_example_queries (agent_id, query, display_order) VALUES
('kafka-topic-agent', 'Create a topic called orders', 1),
('kafka-topic-agent', 'List all topics', 2),
('kafka-topic-agent', 'Describe topic users', 3),
('kafka-topic-agent', 'Delete topic test-topic', 4);

-- Insert example queries for Database agent
INSERT INTO agent_example_queries (agent_id, query, display_order) VALUES
('database-agent', 'Create a table called users with columns id, username, and email', 1),
('database-agent', 'List all tables', 2),
('database-agent', 'Describe table users', 3),
('database-agent', 'Drop table test_table', 4);
