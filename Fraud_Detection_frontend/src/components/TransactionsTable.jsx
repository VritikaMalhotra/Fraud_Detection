import { useMemo } from 'react';
import DecisionBadge from './DecisionBadge.jsx';
import styles from '../styles/TransactionsTable.module.css';

const formatDate = (iso) =>
  new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: 'numeric', month: 'short', day: 'numeric' }).format(
    new Date(iso)
  );

const formatAmount = (amount, currency) => {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch (error) {
    // Fallback for invalid currency codes (e.g., test data with "XX")
    return `${currency} ${amount.toFixed(2)}`;
  }
};

const headerDefs = [
  { key: 'id', label: 'Transaction' },
  { key: 'userId', label: 'User' },
  { key: 'amount', label: 'Amount' },
  { key: 'decision', label: 'Decision' },
  { key: 'score', label: 'Score' },
  { key: 'occurredAt', label: 'Timestamp' }
];

const TransactionsTable = ({ data, onSelect }) => {
  const rows = useMemo(() => data.slice(0, 50), [data]);

  return (
    <div className="card">
      <div className={styles.header}>
        <h3>Transactions</h3>
        <p>{rows.length} most recent</p>
      </div>
      <div className={styles.tableWrapper}>
        <table className="table">
          <thead>
            <tr>
              {headerDefs.map((col) => (
                <th key={col.key}>{col.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((tx) => (
              <tr key={tx.id} onClick={() => onSelect?.(tx)}>
                <td className={styles.id}>{tx.id}</td>
                <td>{tx.userId}</td>
                <td>{formatAmount(tx.amount, tx.currency)}</td>
                <td>
                  <DecisionBadge decision={tx.decision} />
                </td>
                <td>{Math.round(tx.score)}</td>
                <td>{formatDate(tx.occurredAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default TransactionsTable;

