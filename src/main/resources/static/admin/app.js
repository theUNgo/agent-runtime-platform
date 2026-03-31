const { createApp } = Vue;

createApp({
    data() {
        return {
            capabilities: [],
            mcpStatuses: [],
            catalogItems: [],
            searchQuery: "",
            activeType: "",
            flashMessage: "",
            installResult: null,
            loading: {
                any: false,
                installingId: null
            }
        };
    },
    computed: {
        filteredCatalogItems() {
            const normalized = this.searchQuery.trim().toLowerCase();
            return this.catalogItems.filter((item) => {
                const typeMatches = !this.activeType || item.type === this.activeType;
                const text = [
                    item.id,
                    item.name,
                    item.description,
                    ...(item.tags || [])
                ].join(" ").toLowerCase();
                return typeMatches && (!normalized || text.includes(normalized));
            });
        }
    },
    mounted() {
        this.refreshAll();
    },
    methods: {
        async refreshAll() {
            this.loading.any = true;
            try {
                await Promise.all([
                    this.refreshCapabilities(),
                    this.refreshMcp(),
                    this.refreshCatalog()
                ]);
                this.flash("刷新完成。");
            } catch (error) {
                this.flash(this.errorMessage(error));
            } finally {
                this.loading.any = false;
            }
        },
        async refreshCapabilities() {
            this.capabilities = await this.fetchJson("/api/capabilities");
        },
        async refreshMcp() {
            this.mcpStatuses = await this.fetchJson("/api/mcp/servers");
        },
        async refreshCatalog() {
            this.catalogItems = await this.fetchJson("/api/catalog/items");
        },
        async installSkill(itemId) {
            this.loading.installingId = itemId;
            try {
                this.installResult = await this.fetchJson("/api/catalog/skills/install", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ itemId })
                });
                await Promise.all([this.refreshCapabilities(), this.refreshCatalog()]);
                this.flash(this.installResult.message);
            } catch (error) {
                this.flash(this.errorMessage(error));
            } finally {
                this.loading.installingId = null;
            }
        },
        async generateInstallPlan(itemId) {
            this.loading.installingId = itemId;
            try {
                this.installResult = await this.fetchJson(`/api/catalog/mcp-servers/${encodeURIComponent(itemId)}/install-plan`, {
                    method: "POST"
                });
                await this.refreshCatalog();
                this.flash(this.installResult.message);
            } catch (error) {
                this.flash(this.errorMessage(error));
            } finally {
                this.loading.installingId = null;
            }
        },
        statusClass(state) {
            return {
                ok: state === "INITIALIZED",
                warn: state === "CONNECTING",
                error: state === "ERROR" || state === "CLOSED" || state === "DISCONNECTED"
            };
        },
        flash(message) {
            this.flashMessage = message;
            window.clearTimeout(this.flashTimer);
            this.flashTimer = window.setTimeout(() => {
                this.flashMessage = "";
            }, 2600);
        },
        async fetchJson(url, options = {}) {
            const response = await fetch(url, options);
            if (!response.ok) {
                let payload = null;
                try {
                    payload = await response.json();
                } catch (ignored) {
                }
                const error = new Error(payload?.message || `Request failed: ${response.status}`);
                error.payload = payload;
                throw error;
            }
            return response.json();
        },
        errorMessage(error) {
            return error?.payload?.message || error?.message || "请求失败";
        }
    }
}).mount("#app");
