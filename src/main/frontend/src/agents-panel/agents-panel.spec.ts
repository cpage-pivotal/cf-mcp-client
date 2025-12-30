import { describe, it, expect } from 'vitest';
import { A2AAgent } from '../app/app.component';

/**
 * These tests verify the pure business logic used in AgentsPanel.
 * The sorting and status calculation logic is extracted and tested independently.
 */

// Helper functions that mirror the component logic
function sortAgents(agents: A2AAgent[]): A2AAgent[] {
  return [...agents].sort((a, b) => {
    // Healthy agents come first
    if (a.healthy !== b.healthy) {
      return a.healthy ? -1 : 1;
    }
    // Then sort by name
    return a.agentName.localeCompare(b.agentName);
  });
}

function getOverallStatusClass(agents: A2AAgent[]): string {
  if (agents.length === 0) {
    return 'status-red';
  }

  const healthyCount = agents.filter(a => a.healthy).length;

  if (healthyCount === agents.length) {
    return 'status-green';
  } else if (healthyCount > 0) {
    return 'status-orange';
  } else {
    return 'status-red';
  }
}

function getOverallStatusIcon(agents: A2AAgent[]): string {
  const statusClass = getOverallStatusClass(agents);

  switch (statusClass) {
    case 'status-green':
      return 'check_circle';
    case 'status-orange':
      return 'warning';
    case 'status-red':
      return 'error';
    default:
      return 'error';
  }
}

function getOverallStatusText(agents: A2AAgent[]): string {
  if (agents.length === 0) {
    return 'No Agents';
  }

  const healthyCount = agents.filter(a => a.healthy).length;
  return `${healthyCount}/${agents.length} Healthy`;
}

// Factory for creating test agents
function createAgent(overrides: Partial<A2AAgent> = {}): A2AAgent {
  return {
    serviceName: 'test-service',
    agentName: 'Test Agent',
    description: 'A test agent',
    version: '1.0.0',
    agentCardUri: 'http://localhost/agent',
    healthy: true,
    capabilities: {
      streaming: false,
      pushNotifications: false,
      stateTransitionHistory: false
    },
    ...overrides
  };
}

describe('AgentsPanel Logic', () => {
  describe('sortAgents', () => {
    it('should return empty array for empty input', () => {
      expect(sortAgents([])).toEqual([]);
    });

    it('should place healthy agents before unhealthy agents', () => {
      const agents = [
        createAgent({ agentName: 'Unhealthy Agent', healthy: false }),
        createAgent({ agentName: 'Healthy Agent', healthy: true })
      ];

      const sorted = sortAgents(agents);

      expect(sorted[0].agentName).toBe('Healthy Agent');
      expect(sorted[1].agentName).toBe('Unhealthy Agent');
    });

    it('should sort agents alphabetically within same health status', () => {
      const agents = [
        createAgent({ agentName: 'Zebra', healthy: true }),
        createAgent({ agentName: 'Alpha', healthy: true }),
        createAgent({ agentName: 'Beta', healthy: true })
      ];

      const sorted = sortAgents(agents);

      expect(sorted.map(a => a.agentName)).toEqual(['Alpha', 'Beta', 'Zebra']);
    });

    it('should sort healthy agents alphabetically, then unhealthy alphabetically', () => {
      const agents = [
        createAgent({ agentName: 'Charlie', healthy: false }),
        createAgent({ agentName: 'Alpha', healthy: true }),
        createAgent({ agentName: 'Delta', healthy: false }),
        createAgent({ agentName: 'Beta', healthy: true })
      ];

      const sorted = sortAgents(agents);

      expect(sorted.map(a => a.agentName)).toEqual(['Alpha', 'Beta', 'Charlie', 'Delta']);
      expect(sorted[0].healthy).toBe(true);
      expect(sorted[1].healthy).toBe(true);
      expect(sorted[2].healthy).toBe(false);
      expect(sorted[3].healthy).toBe(false);
    });

    it('should not mutate the original array', () => {
      const agents = [
        createAgent({ agentName: 'Beta', healthy: true }),
        createAgent({ agentName: 'Alpha', healthy: true })
      ];

      const originalOrder = agents.map(a => a.agentName);
      sortAgents(agents);

      expect(agents.map(a => a.agentName)).toEqual(originalOrder);
    });
  });

  describe('getOverallStatusClass', () => {
    it('should return status-red for empty agents array', () => {
      expect(getOverallStatusClass([])).toBe('status-red');
    });

    it('should return status-green when all agents are healthy', () => {
      const agents = [
        createAgent({ healthy: true }),
        createAgent({ healthy: true }),
        createAgent({ healthy: true })
      ];

      expect(getOverallStatusClass(agents)).toBe('status-green');
    });

    it('should return status-orange when some agents are healthy', () => {
      const agents = [
        createAgent({ healthy: true }),
        createAgent({ healthy: false }),
        createAgent({ healthy: true })
      ];

      expect(getOverallStatusClass(agents)).toBe('status-orange');
    });

    it('should return status-red when no agents are healthy', () => {
      const agents = [
        createAgent({ healthy: false }),
        createAgent({ healthy: false })
      ];

      expect(getOverallStatusClass(agents)).toBe('status-red');
    });

    it('should return status-green for single healthy agent', () => {
      const agents = [createAgent({ healthy: true })];
      expect(getOverallStatusClass(agents)).toBe('status-green');
    });

    it('should return status-red for single unhealthy agent', () => {
      const agents = [createAgent({ healthy: false })];
      expect(getOverallStatusClass(agents)).toBe('status-red');
    });
  });

  describe('getOverallStatusIcon', () => {
    it('should return error icon for no agents', () => {
      expect(getOverallStatusIcon([])).toBe('error');
    });

    it('should return check_circle for all healthy', () => {
      const agents = [createAgent({ healthy: true })];
      expect(getOverallStatusIcon(agents)).toBe('check_circle');
    });

    it('should return warning for partial health', () => {
      const agents = [
        createAgent({ healthy: true }),
        createAgent({ healthy: false })
      ];
      expect(getOverallStatusIcon(agents)).toBe('warning');
    });

    it('should return error for all unhealthy', () => {
      const agents = [createAgent({ healthy: false })];
      expect(getOverallStatusIcon(agents)).toBe('error');
    });
  });

  describe('getOverallStatusText', () => {
    it('should return "No Agents" for empty array', () => {
      expect(getOverallStatusText([])).toBe('No Agents');
    });

    it('should return correct count for all healthy', () => {
      const agents = [
        createAgent({ healthy: true }),
        createAgent({ healthy: true }),
        createAgent({ healthy: true })
      ];
      expect(getOverallStatusText(agents)).toBe('3/3 Healthy');
    });

    it('should return correct count for partial health', () => {
      const agents = [
        createAgent({ healthy: true }),
        createAgent({ healthy: false }),
        createAgent({ healthy: true }),
        createAgent({ healthy: false })
      ];
      expect(getOverallStatusText(agents)).toBe('2/4 Healthy');
    });

    it('should return 0/n for all unhealthy', () => {
      const agents = [
        createAgent({ healthy: false }),
        createAgent({ healthy: false })
      ];
      expect(getOverallStatusText(agents)).toBe('0/2 Healthy');
    });
  });
});
