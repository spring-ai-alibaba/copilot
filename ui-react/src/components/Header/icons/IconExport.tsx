import React from 'react';

export function IconExport({ className = 'w-6 h-6' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M13 5L21 5M21 5L21 13M21 5L13 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M21 19V17C21 15.8954 20.1046 15 19 15H15M11 15H5C3.89543 15 3 15.8954 3 17V19" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M7 11C9.20914 11 11 9.20914 11 7C11 4.79086 9.20914 3 7 3C4.79086 3 3 4.79086 3 7C3 9.20914 4.79086 11 7 11Z" stroke="currentColor" strokeWidth="2"/>
    </svg>
  );
}