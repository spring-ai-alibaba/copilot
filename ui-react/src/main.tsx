import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import '../global.css';
import { setupFetchInterceptors } from './utils/fetchInterceptor';

// Initialize global fetch interceptors before app mounts
setupFetchInterceptors();

ReactDOM.createRoot(document.getElementById('root')!).render(  
  <React.StrictMode>  
    <App />  
  </React.StrictMode>  
);