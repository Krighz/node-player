/**
 * Node Player — Client Interactivity & 10-Band Realtime EQ
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Header scroll effect
  const header = document.querySelector('header.site-header');
  window.addEventListener('scroll', () => {
    if (window.scrollY > 20) {
      header.classList.add('scrolled');
    } else {
      header.classList.remove('scrolled');
    }
  }, { passive: true });

  // 2. Responsive video background switching
  const bgVideo = document.getElementById('bgVideo');
  if (bgVideo) {
    const isMobile = window.innerWidth <= 768;
    const webmSource = bgVideo.querySelector('source[type="video/webm"]');
    const mp4Source = bgVideo.querySelector('source[type="video/mp4"]');
    if (isMobile) {
      if (webmSource) webmSource.src = 'bg_mobile.webm';
      if (mp4Source) mp4Source.src = 'bg_mobile.mp4';
      bgVideo.load();
    }
  }

  // 3. Mockup Tab Switcher
  const tabButtons = document.querySelectorAll('.tab-btn');
  const screenImages = document.querySelectorAll('.phone-screen img');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      screenImages.forEach(img => img.classList.remove('active'));

      btn.classList.add('active');
      const targetId = btn.getAttribute('data-target');
      const targetImg = document.getElementById(targetId);
      if (targetImg) {
        targetImg.classList.add('active');
      }
    });
  });

  // 4. SHA-256 Hash Copy Button
  const copyBtn = document.getElementById('copyHashBtn');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      const hash = copyBtn.getAttribute('data-hash');
      if (hash) {
        try {
          await navigator.clipboard.writeText(hash);
          const originalText = copyBtn.innerHTML;
          copyBtn.innerHTML = `
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg> Copied!
          `;
          copyBtn.style.color = '#38BDF8';
          setTimeout(() => {
            copyBtn.innerHTML = originalText;
            copyBtn.style.color = '';
          }, 2000);
        } catch (e) {
          console.error('Clipboard copy failed:', e);
        }
      }
    });
  }

  // 5. Interactive 10-Band EQ Playground
  const canvas = document.getElementById('eqCanvas');
  if (canvas) {
    const ctx = canvas.getContext('2d');
    const sliders = document.querySelectorAll('.eq-slider-col input[type=range]');
    const badges = document.querySelectorAll('.gain-badge');

    // 10 bands: 32Hz, 64Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
    const presets = {
      flat: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
      bass: [6.5, 5.0, 3.5, 1.5, 0, 0, 0, 0, 0, 0],
      vocal: [-2.0, -1.0, 0.5, 3.0, 4.5, 3.5, 2.0, 1.0, 0, -1.0],
      electronic: [5.0, 4.0, 2.0, 0, -1.5, 2.0, 3.0, 4.0, 4.5, 5.0],
      acoustic: [3.5, 2.5, 1.0, 0.5, 1.5, 2.0, 3.0, 3.5, 4.0, 3.0],
      rock: [5.0, 3.5, 2.0, 0.5, -1.0, -0.5, 1.5, 3.5, 4.5, 5.0]
    };

    function resizeCanvas() {
      const rect = canvas.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      ctx.scale(dpr, dpr);
      drawCurve();
    }

    function getPoints() {
      const rect = canvas.getBoundingClientRect();
      const width = rect.width;
      const height = rect.height;
      const padX = 36;
      const stepX = (width - padX * 2) / (sliders.length - 1);
      const midY = height / 2;
      const rangeY = (height - 40) / 2; // -12dB to +12dB

      const points = [];
      sliders.forEach((slider, idx) => {
        const val = parseFloat(slider.value); // -12 to +12
        const x = padX + idx * stepX;
        const y = midY - (val / 12.0) * rangeY;
        points.push({ x, y, val });
      });
      return points;
    }

    function drawCurve() {
      const rect = canvas.getBoundingClientRect();
      const width = rect.width;
      const height = rect.height;
      ctx.clearRect(0, 0, width, height);

      // Grid lines
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
      ctx.lineWidth = 1;
      const midY = height / 2;
      const rangeY = (height - 40) / 2;

      // dB lines: +12, +6, 0, -6, -12
      [-12, -6, 0, 6, 12].forEach(db => {
        const y = midY - (db / 12.0) * rangeY;
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();

        ctx.fillStyle = db === 0 ? 'rgba(56, 189, 248, 0.4)' : 'rgba(148, 163, 184, 0.3)';
        ctx.font = '10px monospace';
        ctx.fillText(`${db > 0 ? '+' : ''}${db}dB`, 8, y - 3);
      });

      const points = getPoints();

      // Spline curve through points
      ctx.beginPath();
      ctx.moveTo(0, points[0].y);
      ctx.lineTo(points[0].x, points[0].y);

      for (let i = 0; i < points.length - 1; i++) {
        const p0 = i > 0 ? points[i - 1] : points[i];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = i < points.length - 2 ? points[i + 2] : p2;

        const cp1x = p1.x + (p2.x - p0.x) / 6;
        const cp1y = p1.y + (p2.y - p0.y) / 6;
        const cp2x = p2.x - (p3.x - p1.x) / 6;
        const cp2y = p2.y - (p3.y - p1.y) / 6;

        ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
      }

      ctx.lineTo(width, points[points.length - 1].y);

      // Stroke glowing curve
      ctx.strokeStyle = '#38BDF8';
      ctx.lineWidth = 3;
      ctx.shadowColor = 'rgba(56, 189, 248, 0.6)';
      ctx.shadowBlur = 12;
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Subtle gradient fill under curve
      const lastPt = points[points.length - 1];
      ctx.lineTo(width, height);
      ctx.lineTo(0, height);
      ctx.closePath();
      const grad = ctx.createLinearGradient(0, 0, 0, height);
      grad.addColorStop(0, 'rgba(56, 189, 248, 0.15)');
      grad.addColorStop(1, 'rgba(56, 189, 248, 0.0)');
      ctx.fillStyle = grad;
      ctx.fill();

      // Draw point nodes
      points.forEach(pt => {
        ctx.beginPath();
        ctx.arc(pt.x, pt.y, 5, 0, Math.PI * 2);
        ctx.fillStyle = '#FFFFFF';
        ctx.shadowColor = '#38BDF8';
        ctx.shadowBlur = 8;
        ctx.fill();
        ctx.shadowBlur = 0;

        ctx.strokeStyle = '#38BDF8';
        ctx.lineWidth = 2;
        ctx.stroke();
      });
    }

    // Attach slider events
    sliders.forEach((slider, idx) => {
      slider.addEventListener('input', () => {
        const val = parseFloat(slider.value);
        badges[idx].textContent = `${val > 0 ? '+' : ''}${val.toFixed(1)}dB`;
        drawCurve();
        // Clear preset buttons active state
        document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('active'));
      });
    });

    // Preset buttons
    const presetButtons = document.querySelectorAll('.preset-btn');
    presetButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const pName = btn.getAttribute('data-preset');
        if (presets[pName]) {
          presetButtons.forEach(b => b.classList.remove('active'));
          btn.classList.add('active');

          const targetVals = presets[pName];
          targetVals.forEach((val, idx) => {
            sliders[idx].value = val;
            badges[idx].textContent = `${val > 0 ? '+' : ''}${val.toFixed(1)}dB`;
          });
          drawCurve();
        }
      });
    });

    window.addEventListener('resize', resizeCanvas, { passive: true });
    resizeCanvas();
  }
});
