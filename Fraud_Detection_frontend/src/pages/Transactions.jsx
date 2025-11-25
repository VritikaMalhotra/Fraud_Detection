import { useEffect, useMemo, useState } from 'react';
import TransactionsTable from '../components/TransactionsTable.jsx';
import TransactionDetailDrawer from '../components/TransactionDetailDrawer.jsx';
import DecisionBadge from '../components/DecisionBadge.jsx';
import { fetchTransactions } from '../services/fraudApi.js';
import styles from '../styles/Transactions.module.css';

const decisionFilters = ['ALL', 'ALLOW', 'REVIEW', 'BLOCK'];

const TransactionsPage = () => {
  const [transactions, setTransactions] = useState([]);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await fetchTransactions();
        setTransactions(data);
      } catch (err) {
        console.error('Failed to fetch transactions:', err);
        setError(err.message || 'Failed to load transactions');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const filtered = useMemo(() => {
    const lower = search.toLowerCase();
    return transactions.filter((tx) => {
      const passesFilter = filter === 'ALL' || tx.decision === filter;
      const matchesSearch =
        !search ||
        tx.id.toLowerCase().includes(lower) ||
        tx.userId.toLowerCase().includes(lower) ||
        tx.merchant.toLowerCase().includes(lower);
      return passesFilter && matchesSearch;
    });
  }, [transactions, search, filter]);

  if (loading) {
    return (
      <div className={styles.wrapper}>
        <header className={styles.header}>
          <div>
            <h1>Transactions</h1>
            <p>Loading transactions...</p>
          </div>
        </header>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.wrapper}>
        <header className={styles.header}>
          <div>
            <h1>Transactions</h1>
            <p style={{ color: 'red' }}>Error: {error}</p>
          </div>
        </header>
      </div>
    );
  }

  return (
    <div className={`${styles.wrapper} ${selected ? styles.wrapperWithDrawer : ''}`}>
      <header className={styles.header}>
        <div>
          <h1>Transactions</h1>
          <p>Inspect streaming transactions, enrichment data, and decision results.</p>
        </div>
        <div className={styles.filterBar}>
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by transaction, user, merchant…"
          />
          <div className={styles.filterPills} role="tablist" aria-label="Decision filter">
            {decisionFilters.map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setFilter(value)}
                className={`${styles.pill} ${filter === value ? styles.pillActive : ''}`}
                role="tab"
                aria-selected={filter === value}
              >
                {value === 'ALL' ? 'All' : <DecisionBadge decision={value} />}
              </button>
            ))}
          </div>
        </div>
      </header>

      <TransactionsTable data={filtered} onSelect={setSelected} />
      <TransactionDetailDrawer transaction={selected} onClose={() => setSelected(null)} />
    </div>
  );
};

export default TransactionsPage;

