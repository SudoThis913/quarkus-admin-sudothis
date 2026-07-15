// Reference field search pickers — activated for fields with data-ref-entity
(function () {
    function initRefPickers() {
        document.querySelectorAll('.ref-picker:not([data-ref-init])').forEach(function (picker) {
            picker.setAttribute('data-ref-init', '1');
            var entity = picker.dataset.refEntity;
            var required = picker.dataset.fieldRequired === 'true';
            var searchInput = picker.querySelector('.ref-search');
            var dropdown = picker.querySelector('.ref-dropdown');
            var hiddenInput = picker.querySelector('.ref-id-input');
            var labelDiv = picker.querySelector('.ref-label');

            var currentId = hiddenInput.value;
            if (currentId) {
                fetch('/api/admin/data/' + entity + '/' + currentId)
                    .then(function (r) { return r.ok ? r.json() : null; })
                    .then(function (item) {
                        if (item) {
                            searchInput.value = itemLabel(item);
                            labelDiv.textContent = 'Selected: ' + itemLabel(item) + ' (ID ' + item.id + ')';
                        }
                    })
                    .catch(function () {});
            }

            var debounce;
            searchInput.addEventListener('input', function () {
                clearTimeout(debounce);
                var query = this.value.trim();
                if (!query) {
                    dropdown.style.display = 'none';
                    if (!required) {
                        hiddenInput.value = '';
                        labelDiv.textContent = '';
                    }
                    return;
                }
                debounce = setTimeout(function () {
                    var asNum = Number(query);
                    var isExactNum = !isNaN(asNum) && query === String(asNum) && asNum > 0;
                    var url = isExactNum
                        ? '/api/admin/data/' + entity + '/' + query
                        : '/api/admin/data/' + entity + '?name=' + encodeURIComponent(query) + '&size=10';

                    fetch(url)
                        .then(function (r) { return r.ok ? r.json() : []; })
                        .then(function (data) {
                            var items = Array.isArray(data) ? data : (data && data.id ? [data] : []);
                            renderDropdown(items, picker, required);
                        })
                        .catch(function () { dropdown.style.display = 'none'; });
                }, 300);
            });

            searchInput.addEventListener('blur', function () {
                setTimeout(function () { dropdown.style.display = 'none'; }, 200);
            });

            searchInput.addEventListener('focus', function () {
                if (dropdown.children.length > 0) dropdown.style.display = 'block';
            });
        });
    }

    function itemLabel(item) {
        return item.name || item.username || item.email || ('ID ' + item.id);
    }

    function renderDropdown(items, picker, required) {
        var dropdown = picker.querySelector('.ref-dropdown');
        var searchInput = picker.querySelector('.ref-search');
        var hiddenInput = picker.querySelector('.ref-id-input');
        var labelDiv = picker.querySelector('.ref-label');

        dropdown.innerHTML = '';

        if (!required) {
            var clearBtn = document.createElement('button');
            clearBtn.type = 'button';
            clearBtn.className = 'list-group-item list-group-item-action text-muted fst-italic';
            clearBtn.textContent = '— None (clear) —';
            clearBtn.addEventListener('mousedown', function (e) {
                e.preventDefault();
                hiddenInput.value = '';
                searchInput.value = '';
                labelDiv.textContent = '';
                dropdown.style.display = 'none';
            });
            dropdown.appendChild(clearBtn);
        }

        items.forEach(function (item) {
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'list-group-item list-group-item-action';
            btn.textContent = itemLabel(item) + ' (ID ' + item.id + ')';
            btn.addEventListener('mousedown', function (e) {
                e.preventDefault();
                hiddenInput.value = item.id;
                searchInput.value = itemLabel(item);
                labelDiv.textContent = 'Selected: ' + itemLabel(item) + ' (ID ' + item.id + ')';
                dropdown.style.display = 'none';
            });
            dropdown.appendChild(btn);
        });

        dropdown.style.display = (items.length > 0 || !required) ? 'block' : 'none';
    }

    document.addEventListener('DOMContentLoaded', initRefPickers);
    document.addEventListener('htmx:afterSettle', initRefPickers);
}());

// Toast notification handlers — wired to HTMX custom events
(function () {
    function showToast(message, variant) {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        const el = document.createElement('div');
        el.className = 'toast align-items-center text-white border-0 bg-' + variant;
        el.setAttribute('role', 'alert');
        el.setAttribute('aria-live', 'assertive');
        el.setAttribute('aria-atomic', 'true');
        el.innerHTML =
            '<div class="d-flex">' +
            '<div class="toast-body">' + message + '</div>' +
            '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>' +
            '</div>';
        container.appendChild(el);
        const toast = new bootstrap.Toast(el);
        toast.show();
        el.addEventListener('hidden.bs.toast', () => el.remove());
    }

    document.addEventListener('showMessage', function (evt) {
        showToast(evt.detail.value, 'success');
    });
    document.addEventListener('showError', function (evt) {
        showToast(evt.detail.value, 'danger');
    });

    // On direct navigation to /admin/{entity}, auto-load the fragment via HTMX
    document.addEventListener('DOMContentLoaded', function () {
        const params = new URLSearchParams(window.location.search);
        const deepPath = params.get('path');
        if (deepPath && window.htmx) {
            htmx.ajax('GET', deepPath, { target: '#main-content', swap: 'innerHTML' });
            history.replaceState(null, '', deepPath);
        }
    });

    // Highlight active sidebar link based on current URL path
    document.addEventListener('htmx:pushedIntoHistory', updateActiveLink);
    document.addEventListener('DOMContentLoaded', updateActiveLink);

    function updateActiveLink() {
        const path = window.location.pathname;
        document.querySelectorAll('#admin-nav .nav-link').forEach(function (a) {
            a.classList.toggle('active', a.getAttribute('href') === path);
        });
    }
}());
