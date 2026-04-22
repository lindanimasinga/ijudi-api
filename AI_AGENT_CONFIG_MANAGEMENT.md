# AI Agent Configuration Management

## Overview

The AI agent system prompts are now stored in MongoDB instead of being hardcoded. This allows dynamic configuration and prompt updates without code changes or redeployment.

---

## MongoDB Collection Structure

**Collection:** `ai_agent_configs`

```javascript
{
  "_id": ObjectId("..."),
  "agentName": "driver_support",
  "systemPrompt": "# Customer Service Agent for Drivers\n...",
  "description": "AI agent for driver support and onboarding via WhatsApp",
  "active": true,
  "createdAt": ISODate("2026-04-19T10:00:00Z"),
  "updatedAt": ISODate("2026-04-19T10:00:00Z"),
  "version": 1
}
```

---

## Architecture

### Components

1. **AiAgentConfig** - MongoDB document entity
2. **AiAgentConfigRepository** - Spring Data MongoDB repository
3. **AiAgentConfigService** - Business logic for config management
4. **AiAgentConfigInitializer** - Startup initialization (creates default configs)
5. **AiAgentConfigController** - REST API for config management
6. **AiCustomerServiceAgent** - Updated to load prompts from database

### Flow

```
WhatsApp Message
  ↓
AiCustomerServiceAgent.handleWhatsappQuery()
  ↓
AiAgentConfigService.getSystemPrompt("driver_support")
  ↓
AiAgentConfigRepository.findByAgentNameAndActiveTrue()
  ↓
MongoDB: ai_agent_configs collection
  ↓
System prompt returned to AI agent
  ↓
OpenAI API call with loaded prompt
```

---

## REST API Endpoints

### Get All Agent Configurations
```
GET /api/ai/agents
```
Returns all agent configs

### Get Agent by ID
```
GET /api/ai/agents/{id}
```
Returns a specific agent config by MongoDB ID

### Get Agent by Name
```
GET /api/ai/agents/name/{agentName}
```
Example: `GET /api/ai/agents/name/driver_support`

### Create or Update Agent
```
POST /api/ai/agents
Content-Type: application/json

{
  "agentName": "driver_support",
  "systemPrompt": "# Customer Service Agent...",
  "description": "Driver support agent",
  "active": true
}
```

### Update Existing Agent
```
PUT /api/ai/agents/{id}
Content-Type: application/json

{
  "agentName": "driver_support",
  "systemPrompt": "# Updated prompt...",
  "description": "Updated description",
  "active": true
}
```

### Activate Agent
```
POST /api/ai/agents/{id}/activate
```
Makes the agent active (status = true)

### Deactivate Agent
```
POST /api/ai/agents/{id}/deactivate
```
Makes the agent inactive (status = false)

### Delete Agent
```
DELETE /api/ai/agents/{id}
```
Removes the agent config entirely

---

## MongoDB Queries

### View All Agent Configs
```javascript
db.ai_agent_configs.find()
```

### View Specific Agent
```javascript
db.ai_agent_configs.findOne({ agentName: "driver_support" })
```

### Update Agent Prompt
```javascript
db.ai_agent_configs.updateOne(
  { agentName: "driver_support" },
  { 
    $set: { 
      systemPrompt: "New prompt here...",
      updatedAt: new Date(),
      version: { $inc: 1 }
    } 
  }
)
```

### Deactivate Agent
```javascript
db.ai_agent_configs.updateOne(
  { agentName: "driver_support" },
  { $set: { active: false } }
)
```

### Activate Agent
```javascript
db.ai_agent_configs.updateOne(
  { agentName: "driver_support" },
  { $set: { active: true } }
)
```

---

## Initialization

On application startup, `AiAgentConfigInitializer` automatically:

1. Checks if "driver_support" agent config exists
2. If not, creates it with the default system prompt
3. Sets it to `active: true`

This ensures the agent always has a default configuration.

---

## Usage Example

### In Code
```java
@Service
public class MyService {
    
    private final AiAgentConfigService configService;
    
    public MyService(AiAgentConfigService configService) {
        this.configService = configService;
    }
    
    public void useAgentPrompt() {
        // Get prompt by agent name
        String prompt = configService.getSystemPrompt("driver_support");
        
        // Or get full config
        Optional<AiAgentConfig> config = configService.getAgentConfig("driver_support");
        
        // Update prompt
        configService.saveAgentConfig(
            "driver_support",
            "New prompt...",
            "Updated description"
        );
    }
}
```

### Via REST API
```bash
# Get current prompt
curl -X GET http://localhost:8080/api/ai/agents/name/driver_support

# Update prompt
curl -X PUT http://localhost:8080/api/ai/agents/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "agentName": "driver_support",
    "systemPrompt": "New prompt content here...",
    "description": "Updated description",
    "active": true
  }'

# Activate agent
curl -X POST http://localhost:8080/api/ai/agents/{id}/activate

# Deactivate agent
curl -X POST http://localhost:8080/api/ai/agents/{id}/deactivate
```

---

## Benefits

✅ **Dynamic Configuration** - Update prompts without code changes  
✅ **A/B Testing** - Create multiple configs, activate/deactivate as needed  
✅ **Versioning** - Track prompt changes with version numbers  
✅ **Audit Trail** - `createdAt`, `updatedAt` timestamps  
✅ **Zero Downtime** - Changes take effect immediately  
✅ **Multi-Agent Support** - Manage multiple agents (customer_support, sales, etc.)  

---

## Adding a New Agent

1. Create a new agent config via REST API:
```bash
curl -X POST http://localhost:8080/api/ai/agents \
  -H "Content-Type: application/json" \
  -d '{
    "agentName": "customer_support",
    "systemPrompt": "You are a customer support agent...",
    "description": "Customer support via WhatsApp",
    "active": true
  }'
```

2. Reference it in your code:
```java
String prompt = configService.getSystemPrompt("customer_support");
```

3. Use it in your message handler:
```java
String reply = callOpenAi(prompt, userMessage);
```

---

## Testing

Unit tests are available in `AiAgentConfigServiceTest.java`:

```bash
mvn test -Dtest=AiAgentConfigServiceTest
```

Tests cover:
- Retrieving prompts by agent name
- Creating new agent configs
- Updating existing configs
- Activating/deactivating agents
- Versioning on updates

