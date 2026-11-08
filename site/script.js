// Node Player Site JavaScript
document.addEventListener('DOMContentLoaded', () => {
  // Theme Toggle
  const toggleBtn = document.getElementById('themeToggle');
  const currentTheme = localStorage.getItem('nodeplayer_theme') || 
    (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  
  document.documentElement.setAttribute('data-theme', currentTheme);

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      const active = document.documentElement.getAttribute('data-theme');
      const next = active === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('nodeplayer_theme', next);
    });
  }

  // OS Detection for Download Highlight
  const isWindows = navigator.userAgent.indexOf('Win') !== -1;
  const isAndroid = navigator.userAgent.indexOf('Android') !== -1;
  const cardWin = document.getElementById('cardWindows');
  const cardAndroid = document.getElementById('cardAndroid');
  const badgeWin = document.getElementById('badgeWindows');
  const badgeAndroid = document.getElementById('badgeAndroid');

  if (isWindows && cardWin && cardAndroid) {
    cardWin.classList.add('highlighted');
    cardAndroid.classList.remove('highlighted');
    if (badgeWin) badgeWin.style.display = 'block';
    if (badgeAndroid) badgeAndroid.style.display = 'none';
  } else if (cardAndroid) {
    cardAndroid.classList.add('highlighted');
    if (cardWin) cardWin.classList.remove('highlighted');
    if (badgeAndroid) badgeAndroid.style.display = 'block';
    if (badgeWin) badgeWin.style.display = 'none';
  }

  // Carousel Controls
  const track = document.getElementById('carouselTrack');
  const prevBtn = document.getElementById('prevSlide');
  const nextBtn = document.getElementById('nextSlide');

  if (track && prevBtn && nextBtn) {
    prevBtn.addEventListener('click', () => {
      track.scrollBy({ left: -312, behavior: 'smooth' });
    });
    nextBtn.addEventListener('click', () => {
      track.scrollBy({ left: 312, behavior: 'smooth' });
    });
  }

  // Hero Parallax Tilt
  const mockup = document.getElementById('heroMockup');
  if (mockup && window.matchMedia('(pointer: fine)').matches) {
    window.addEventListener('mousemove', (e) => {
      const { clientX, clientY } = e;
      const xRatio = (clientX / window.innerWidth - 0.5) * 12;
      const yRatio = (clientY / window.innerHeight - 0.5) * -12;
      mockup.style.transform = `rotateY(${xRatio}deg) rotateX(${yRatio}deg)`;
    });
  }

  // FAQ Accordion
  document.querySelectorAll('.faq-item').forEach(item => {
    item.addEventListener('click', () => {
      item.classList.toggle('open');
      const span = item.querySelector('.faq-question span');
      if (span) span.textContent = item.classList.contains('open') ? '−' : '+';
    });
  });
});
