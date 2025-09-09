<template>
  <header class="header">
    <div class="container">
      <nav class="navbar">
        <div class="logo">
          <i class="fas fa-cube"></i>
          <span>VueDesign</span>
        </div>
        <ul class="nav-links">
          <li><a href="#" class="nav-link">Home</a></li>
          <li><a href="#" class="nav-link">Features</a></li>
          <li><a href="#" class="nav-link">Pricing</a></li>
          <li><a href="#" class="nav-link">Contact</a></li>
        </ul>
        <div class="nav-actions">
          <button class="btn btn-outline">Sign In</button>
          <button class="btn btn-primary">Get Started</button>
        </div>
      </nav>
    </div>
  </header>
</template>

<script setup>
</script>

<style scoped>
.header {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 100;
  padding: 16px 0;
  border-radius: 16px;
  margin-bottom: 40px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary);
}

.logo i {
  margin-right: 8px;
  font-size: 1.8rem;
}

.nav-links {
  display: flex;
  list-style: none;
}

.nav-link {
  text-decoration: none;
  color: var(--dark);
  font-weight: 500;
  margin: 0 16px;
  transition: var(--transition);
  position: relative;
}

.nav-link:hover {
  color: var(--primary);
}

.nav-link::after {
  content: '';
  position: absolute;
  bottom: -5px;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--primary);
  transition: var(--transition);
}

.nav-link:hover::after {
  width: 100%;
}

.nav-actions {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
}
</style>
