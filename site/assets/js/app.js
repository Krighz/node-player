/**
 * Node Player — Official Web Application Scripts
 * Manages GitHub Releases API fetching, Hero mode switcher,
 * Changelog filtering, and clipboard interactions.
 */

document.addEventListener('DOMContentLoaded', () => {
  initHeroSwitcher();
  initReleaseHub();
  initChangelogFilter();
  initClipboardButtons();
});

/* ==========================================================================
   Hero Mode Switcher
   ========================================================================== */
function initHeroSwitcher() {
  const btnYt = document.getElementById('hero-mode-yt');
  const btnFiles = document.getElementById('hero-mode-files');
  const phoneScreen = document.getElementById('hero-phone-screen');
  const stat1Val = document.getElementById('stat-1-val');
  const stat1Label = document.getElementById('stat-1-label');
  const stat2Val = document.getElementById('stat-2-val');
  const stat2Label = document.getElementById('stat-2-label');
  const stat3Val = document.getElementById('stat-3-val');
  const stat3Label = document.getElementById('stat-3-label');
  const stat4Val = document.getElementById('stat-4-val');
  const stat4Label = document.getElementById('stat-4-label');

  if (!btnYt || !btnFiles || !phoneScreen) return;

  btnYt.addEventListener('click', () => {
    btnYt.classList.add('active');
    btnFiles.classList.remove('active');

    // Update stats for YouTube Music
    if (stat1Val) stat1Val.textContent = '~1.1s';
    if (stat1Label) stat1Label.textContent = 'Stream Startup';
    if (stat2Val) stat2Val.textContent = '128 KB';
    if (stat2Label) stat2Label.textContent = 'Chunked Buffer';
    if (stat3Val) stat3Val.textContent = '6 Shelves';
    if (stat3Label) stat3Label.textContent = 'Curated Moods & Mixes';
    if (stat4Val) stat4Val.textContent = '16:9';
    if (stat4Label) stat4Label.textContent = 'Music Video Support';

    // Update Phone Display
    phoneScreen.innerHTML = `
      <img src="screenshots/01_home_ytmusic.webp" alt="Node Player YouTube Music Home" style="width:100%; height:100%; object-fit:cover;" />
    `;
  });

  btnFiles.addEventListener('click', () => {
    btnFiles.classList.add('active');
    btnYt.classList.remove('active');

    // Update stats for Local Audio
    if (stat1Val) stat1Val.textContent = '32-Bit';
    if (stat1Label) stat1Label.textContent = 'Pure Float DSP';
    if (stat2Val) stat2Val.textContent = 'EBU R128';
    if (stat2Label) stat2Label.textContent = 'Loudness Matched';
    if (stat3Val) stat3Val.textContent = '10-Band';
    if (stat3Label) stat3Label.textContent = 'AutoEQ + Biquad EQ';
    if (stat4Val) stat4Val.textContent = 'WASAPI / Sinks';
    if (stat4Label) stat4Label.textContent = 'Bit-Perfect Direct';

    // Update Phone Display with video loop or now playing
    phoneScreen.innerHTML = `
      <video autoplay loop muted playsinline poster="screenshots/02_now_playing.webp" style="width:100%; height:100%; object-fit:cover;">
        <source src="assets/media/demo_player_motion.mp4" type="video/mp4" />
        <img src="screenshots/02_now_playing.webp" alt="Node Player Now Playing" />
      </video>
    `;
  });
}

/* ==========================================================================
   GitHub Release Hub API
   ========================================================================== */
async function initReleaseHub() {
  const versionBadge = document.getElementById('release-version-badge');
  const apkDownloadBtn = document.getElementById('btn-download-apk');
  const apkSizeLabel = document.getElementById('apk-size-label');
  const winDownloadBtn = document.getElementById('btn-download-win');
  const winSizeLabel = document.getElementById('win-size-label');
  const shaLabel = document.getElementById('sha256-hash-label');

  const repo = 'Krighz/node-player';
  const apiUrl = `https://api.github.com/repos/${repo}/releases/latest`;

  // Fallback defaults in case of network restrictions
  let version = 'v2.0.0';
  let apkUrl = `https://github.com/${repo}/releases/tag/v2.0.0`;
  let winUrl = `https://github.com/${repo}/releases/tag/v2.0.0`;
  let apkSize = '~50 MB';
  let winSize = '~120 MB';
  let shaSummary = 'SHA256SUMS.txt verified release key';

  try {
    const res = await fetch(apiUrl, { headers: { 'Accept': 'application/vnd.github.v3+json' } });
    if (res.ok) {
      const data = await res.json();
      version = data.tag_name || version;

      if (data.assets && Array.isArray(data.assets)) {
        const apkAsset = data.assets.find(a => a.name.endsWith('.apk'));
        if (apkAsset) {
          apkUrl = apkAsset.browser_download_url;
          apkSize = formatBytes(apkAsset.size);
        }

        const winAsset = data.assets.find(a => a.name.endsWith('.zip') || a.name.endsWith('.msi') || a.name.endsWith('.exe'));
        if (winAsset) {
          winUrl = winAsset.browser_download_url;
          winSize = formatBytes(winAsset.size);
        }

        const shaAsset = data.assets.find(a => a.name.toLowerCase().includes('sha256'));
        if (shaAsset) {
          shaSummary = `${shaAsset.name} available on GitHub Releases`;
        }
      }
    }
  } catch (err) {
    console.warn('Using static release fallbacks:', err);
  }

  if (versionBadge) versionBadge.textContent = version;
  if (apkDownloadBtn) apkDownloadBtn.href = apkUrl;
  if (apkSizeLabel) apkSizeLabel.textContent = `${version} • ${apkSize} • Android 8.0+`;
  if (winDownloadBtn) winDownloadBtn.href = winUrl;
  if (winSizeLabel) winSizeLabel.textContent = `${version} • ${winSize} • Windows 10/11 x64`;
  if (shaLabel) shaLabel.textContent = shaSummary;
}

function formatBytes(bytes, decimals = 1) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

/* ==========================================================================
   Changelog Filter
   ========================================================================== */
function initChangelogFilter() {
  const filterBtns = document.querySelectorAll('.changelog-filter-btn');
  const entries = document.querySelectorAll('.changelog-entry');

  if (!filterBtns.length || !entries.length) return;

  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const filter = btn.getAttribute('data-filter') || 'all';

      entries.forEach(entry => {
        const tags = (entry.getAttribute('data-tags') || '').split(' ');
        if (filter === 'all' || tags.includes(filter)) {
          entry.style.display = 'block';
        } else {
          entry.style.display = 'none';
        }
      });
    });
  });
}

/* ==========================================================================
   Clipboard Helpers
   ========================================================================== */
function initClipboardButtons() {
  document.querySelectorAll('[data-copy]').forEach(el => {
    el.addEventListener('click', async (e) => {
      e.preventDefault();
      const textToCopy = el.getAttribute('data-copy');
      if (!textToCopy) return;

      try {
        await navigator.clipboard.writeText(textToCopy);
        const originalText = el.textContent;
        el.textContent = 'Copied!';
        el.style.borderColor = '#5EEAD4';
        setTimeout(() => {
          el.textContent = originalText;
          el.style.borderColor = '';
        }, 2000);
      } catch (err) {
        console.error('Failed to copy text:', err);
      }
    });
  });
}
