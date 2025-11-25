// Use relative URLs to leverage Vite proxy and avoid CORS issues
const API_BASE = import.meta.env.VITE_API_BASE ?? '';
const INGEST_BASE = import.meta.env.VITE_INGEST_BASE ?? '';

const headers = {
  'Content-Type': 'application/json'
};

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers,
    ...options
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed with status ${response.status}`);
  }
  return response.json();
}

export const api = {
  overview: () => request(`${API_BASE}/api/overview`),
  recentDecisions: (size = 25, page = 0, params = {}) => {
    const queryParams = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      ...params
    });
    return request(`${API_BASE}/api/decisions?${queryParams}`);
  },
  highRisk: (size = 10) => request(`${API_BASE}/api/decisions/high-risk?size=${size}`),
  recentTransactions: (limit = 25) =>
    request(`${API_BASE}/api/transactions/recent?limit=${limit}`),
  transactionsSince: (minutes = 60) =>
    request(`${API_BASE}/api/transactions/since?minutes=${minutes}`),
  getDecision: (transactionId) =>
    request(`${API_BASE}/api/decisions/${transactionId}`),
  getUserStats: (userId) =>
    request(`${API_BASE}/api/decisions/user/${userId}/stats`),
  createTransaction: (payload) =>
    fetch(`${INGEST_BASE}/transactions`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    }).then(async (res) => {
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Request failed with status ${res.status}`);
      }
      return res;
    })
};

