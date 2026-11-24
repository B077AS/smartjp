<template>
  <div class="wordmark">
    <!-- The logo (public/logo.svg): fixed colours, it does not follow the accent. -->
    <img class="logo" src="/logo.svg" alt="" width="32" height="32" />
    <span class="name">SmartJP</span>
  </div>
</template>

<style scoped>
.wordmark {
  display: flex;
  align-items: center;
  gap: 8px;
}
.logo {
  display: block;
  width: 32px;
  height: 32px;
}
.name {
  font-family: var(--font-display);
  font-size: 21px;
}
</style>
