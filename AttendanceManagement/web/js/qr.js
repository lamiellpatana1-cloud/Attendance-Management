// QR code scanning using jsQR library.
// Loaded from CDN: https://cdn.jsdelivr.net/npm/jsqr/dist/jsQR.js

let qrStream = null;
let qrAnimFrame = null;
const qrRecentScans = new Map();
const QR_SCAN_COOLDOWN_MS = 2500;

function qrLocalDate(date = new Date()) {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return localDate.toISOString().split('T')[0];
}

function qrLocalTime(date = new Date()) {
  return date.toTimeString().split(' ')[0];
}

async function startQRScan() {
  try {
    qrRecentScans.clear();
    qrStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 }
      }
    });
    const video = document.getElementById('qrVideo');
    video.srcObject = qrStream;
    video.setAttribute('playsinline', 'true');
    video.muted = true;
    video.play();

    if (!window.jsQR) {
      const script = document.createElement('script');
      script.src = 'https://cdn.jsdelivr.net/npm/jsqr/dist/jsQR.js';
      script.onload = () => scanLoop();
      document.head.appendChild(script);
    } else {
      scanLoop();
    }
    showToast('Camera started', 'success');
  } catch (e) {
    showToast('Camera access denied or not available', 'error');
  }
}

function scanLoop() {
  const video = document.getElementById('qrVideo');
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');

  function tick() {
    if (video.readyState === video.HAVE_ENOUGH_DATA) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(imageData.data, imageData.width, imageData.height, { inversionAttempts: 'attemptBoth' });
      if (code && code.data) {
        const normalized = String(code.data).trim();
        const now = Date.now();
        const lastSeen = qrRecentScans.get(normalized) || 0;
        if (normalized && now - lastSeen > QR_SCAN_COOLDOWN_MS) {
          qrRecentScans.set(normalized, now);
          processQRData(normalized);
        }
      }
    }
    qrAnimFrame = requestAnimationFrame(tick);
  }

  tick();
}

function stopQRScan() {
  if (qrStream) {
    qrStream.getTracks().forEach(track => track.stop());
    qrStream = null;
  }
  if (qrAnimFrame) {
    cancelAnimationFrame(qrAnimFrame);
    qrAnimFrame = null;
  }
  qrRecentScans.clear();
  showToast('Camera stopped', 'info');
}

function processQRManual() {
  const value = document.getElementById('qrManualInput').value.trim();
  if (!value) {
    showToast('Enter QR data', 'error');
    return;
  }
  processQRData(value);
  document.getElementById('qrManualInput').value = '';
}

async function processQRData(data) {
  const rawData = String(data || '').trim();
  if (!rawData) {
    showToast('Invalid QR code', 'error');
    return;
  }

  const now = new Date();
  const subjectSelect = document.getElementById('qrSubject') || document.getElementById('attSubject');
  const dateInput = document.getElementById('qrDate');
  const subjectId = subjectSelect && subjectSelect.value ? subjectSelect.value : '';
  const date = dateInput && dateInput.value ? dateInput.value : qrLocalDate(now);
  const timeIn = qrLocalTime(now);

  if (!subjectId) {
    showToast('Select a subject before scanning', 'error');
    return;
  }

  const res = await API.post('/api/qr', {
    qr_data: rawData,
    subject_id: subjectId,
    date,
    time_in: timeIn
  });

  const log = document.getElementById('qrLog');
  const entry = document.createElement('div');
  const fallbackParts = rawData.split('|');
  const studentNumber = res.student_id || fallbackParts[1] || fallbackParts[0] || '-';
  const name = res.name || fallbackParts[2] || 'Student';
  entry.className = 'qr-log-entry ' + (res.message ? 'success' : 'error');
  entry.innerHTML = `<span class="qr-log-name">${name}</span>
    <span class="qr-log-id">${studentNumber}</span>
    <span class="qr-log-time">${res.time_in || timeIn}</span>
    <span class="qr-log-status">${res.message ? 'Saved' : 'ERR'}</span>`;

  if (log.querySelector('.empty-msg')) log.innerHTML = '';
  log.prepend(entry);

  if (res.message && typeof loadRecords === 'function') {
    const recDate = document.getElementById('recDate');
    if (recDate) recDate.value = res.date || date;
    loadRecords();
  }

  showToast(res.message ? `${name} marked present` : 'Error: ' + res.error, res.message ? 'success' : 'error');
}
