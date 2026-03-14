// ===== DATA STORE (localStorage) =====
const STORAGE_KEY = 'titanfit_members';

function getMembers() {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
}

function saveMembers(members) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(members));
}

function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2, 5);
}

// ===== HAMBURGER MENU =====
function toggleMenu() {
    const menu = document.getElementById('navMenu');
    const btn = document.getElementById('hamburgerBtn');
    menu.classList.toggle('show');
    btn.classList.toggle('active');
}

// ===== NAVIGATION =====
function switchSection(section, clickedLink) {
    // Update nav active state
    document.querySelectorAll('#option a').forEach(a => a.classList.remove('nav-active'));
    if (clickedLink) clickedLink.classList.add('nav-active');

    // Switch sections
    document.querySelectorAll('.admin-section').forEach(s => s.classList.remove('active'));
    const target = document.getElementById(section + 'Section');
    if (target) target.classList.add('active');

    // Close mobile menu
    document.getElementById('navMenu').classList.remove('show');
    document.getElementById('hamburgerBtn').classList.remove('active');

    refreshAll();
}

// ===== CRUD OPERATIONS =====

// CREATE / UPDATE
const memberForm = document.getElementById('memberForm');
const editIdField = document.getElementById('editId');
const formTitle = document.getElementById('formTitle');
const submitBtn = document.getElementById('submitBtn');
const cancelBtn = document.getElementById('cancelBtn');

memberForm.addEventListener('submit', (e) => {
    e.preventDefault();

    const name = document.getElementById('memberName').value.trim();
    const pkg = document.getElementById('memberPackage').value;
    const fees = parseFloat(document.getElementById('memberFees').value);
    const date = document.getElementById('memberDate').value;

    if (!name || !pkg || isNaN(fees) || !date) {
        showToast('Please fill all fields', 'error');
        return;
    }

    const members = getMembers();
    const editId = editIdField.value;

    if (editId) {
        // UPDATE
        const idx = members.findIndex(m => m.id === editId);
        if (idx !== -1) {
            members[idx] = { ...members[idx], name, package: pkg, fees, joiningDate: date };
            saveMembers(members);
            showToast('Member updated successfully!', 'success');
        }
        cancelEdit();
    } else {
        // CREATE
        const newMember = {
            id: generateId(),
            name,
            package: pkg,
            fees,
            joiningDate: date
        };
        members.push(newMember);
        saveMembers(members);
        showToast('Member added successfully!', 'success');
    }

    memberForm.reset();
    refreshAll();
});

// EDIT MODE
function startEdit(id) {
    const members = getMembers();
    const member = members.find(m => m.id === id);
    if (!member) return;

    editIdField.value = member.id;
    document.getElementById('memberName').value = member.name;
    document.getElementById('memberPackage').value = member.package;
    document.getElementById('memberFees').value = member.fees;
    document.getElementById('memberDate').value = member.joiningDate;

    formTitle.textContent = '✏️ Edit Member';
    submitBtn.textContent = 'Update Member';
    cancelBtn.style.display = 'inline-block';

    // Scroll to form
    formTitle.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function cancelEdit() {
    editIdField.value = '';
    memberForm.reset();
    formTitle.textContent = '➕ Add New Member';
    submitBtn.textContent = 'Add Member';
    cancelBtn.style.display = 'none';
}

// DELETE
let deleteTargetId = null;

function confirmDelete(id) {
    deleteTargetId = id;
    document.getElementById('deleteModal').classList.add('show');
}

document.getElementById('confirmDeleteBtn').addEventListener('click', () => {
    if (deleteTargetId) {
        let members = getMembers();
        members = members.filter(m => m.id !== deleteTargetId);
        saveMembers(members);
        showToast('Member deleted', 'info');
        refreshAll();
    }
    closeModal();
});

function closeModal() {
    document.getElementById('deleteModal').classList.remove('show');
    deleteTargetId = null;
}

// ===== FILTERS =====
function applyFilters() {
    const name = document.getElementById('filterName').value.toLowerCase().trim();
    const pkg = document.getElementById('filterPackage').value;
    const from = document.getElementById('filterDateFrom').value;
    const to = document.getElementById('filterDateTo').value;

    let members = getMembers();

    if (name) members = members.filter(m => m.name.toLowerCase().includes(name));
    if (pkg) members = members.filter(m => m.package === pkg);
    if (from) members = members.filter(m => m.joiningDate >= from);
    if (to) members = members.filter(m => m.joiningDate <= to);

    renderMembersTable(members);
}

function clearFilters() {
    document.getElementById('filterName').value = '';
    document.getElementById('filterPackage').value = '';
    document.getElementById('filterDateFrom').value = '';
    document.getElementById('filterDateTo').value = '';
    applyFilters();
}

// ===== RENDER =====

function formatDate(dateStr) {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatCurrency(amount) {
    return '₹' + Number(amount).toLocaleString('en-IN');
}

function renderMembersTable(members) {
    const tbody = document.getElementById('membersTableBody');
    const empty = document.getElementById('membersEmpty');
    const countEl = document.getElementById('filteredCount');

    if (members.length === 0) {
        tbody.innerHTML = '';
        empty.style.display = 'block';
        countEl.textContent = '';
        return;
    }

    empty.style.display = 'none';
    countEl.textContent = `(${members.length})`;

    members.sort((a, b) => new Date(b.joiningDate) - new Date(a.joiningDate));

    tbody.innerHTML = members.map((m, i) => `
    <tr>
      <td>${i + 1}</td>
      <td><strong>${escapeHtml(m.name)}</strong></td>
      <td><span class="pkg-badge">${m.package}</span></td>
      <td>${formatCurrency(m.fees)}</td>
      <td>${formatDate(m.joiningDate)}</td>
      <td class="actions-cell">
        <button class="edit-btn" onclick="startEdit('${m.id}')">Edit</button>
        <button class="del-btn" onclick="confirmDelete('${m.id}')">Delete</button>
      </td>
    </tr>
  `).join('');
}

function renderRecentMembers() {
    const members = getMembers();
    const tbody = document.getElementById('recentMembersBody');
    const empty = document.getElementById('recentEmpty');

    if (members.length === 0) {
        tbody.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';
    const recent = [...members]
        .sort((a, b) => new Date(b.joiningDate) - new Date(a.joiningDate))
        .slice(0, 5);

    tbody.innerHTML = recent.map(m => `
    <tr>
      <td><strong>${escapeHtml(m.name)}</strong></td>
      <td><span class="pkg-badge">${m.package}</span></td>
      <td>${formatCurrency(m.fees)}</td>
      <td>${formatDate(m.joiningDate)}</td>
    </tr>
  `).join('');
}

function updateStats() {
    const members = getMembers();
    document.getElementById('totalMembers').textContent = members.length;
    document.getElementById('totalFees').textContent = formatCurrency(
        members.reduce((sum, m) => sum + (m.fees || 0), 0)
    );
    document.getElementById('package1Count').textContent = members.filter(m => m.package === 'Starter Fit').length;
    document.getElementById('package2Count').textContent = members.filter(m => m.package === 'Titan Pro').length;
    document.getElementById('package3Count').textContent = members.filter(m => m.package === 'Elite Warrior').length;
    document.getElementById('package4Count').textContent = members.filter(m => m.package === 'Weekend Hustler').length;
}

function refreshAll() {
    updateStats();
    renderRecentMembers();
    applyFilters();
}

// ===== UTILITIES =====
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast ' + type + ' show';
    setTimeout(() => toast.classList.remove('show'), 2500);
}

// ===== LOGOUT =====
function logoutAdmin() {
    sessionStorage.removeItem('isAdminLoggedIn');
    window.location.href = 'admin-login.html';
}

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('memberDate').valueAsDate = new Date();
    refreshAll();
});
