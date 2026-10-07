import { Component, signal } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { DatePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
interface Job {
  id: string;
  title: string;
  state: string | null;
}
interface Proof {
  id: string;
  number: number;
  specs: string;
  state: string;
  title?: string;
  digest: string;
  name: string;
  comment: string;
  decided_at: string;
}
@Component({
  selector: "app-root",
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: ` <header>
      <a href="/" class="brand"
        ><span class="mark">P</span> Print Approval System</a
      ><span class="tagline">Artwork review workspace</span>
      @if (logged()) {
        <button class="quiet" (click)="logout()">Sign out</button>
      }
    </header>
    <main [class.customer]="!!token" [class.workspace]="!token">
      <div class="notice" role="alert" [hidden]="!error()">
        {{ error() }}
        <button class="quiet" (click)="refresh()">Try again</button>
      </div>
      @if (token) {
        <div class="eyebrow">YOUR PRINT REVIEW</div>
        @if (proof(); as p) {
          <h1>{{ p.title }}</h1>
          <p class="lead">
            Check the artwork and production details. Your decision applies to this version only.
          </p>
          <div class="proof-grid">
            <section class="preview">
              <img
                [src]="'/api/proof/' + token + '/image'"
                alt="Design preview for customer approval"
              />
            </section>
            <section class="card decision">
              <span class="badge" [attr.data-state]="p.state">Version {{ p.number }} · {{ p.state }}</span>
              <h2>Print specifications</h2>
              <p class="specs">{{ p.specs }}</p>
              <p class="small">
                Screen previews are not exact print color proofs. Your entered
                name is recorded, but is not verified identity or a certified
                electronic signature.
              </p>
              @if (p.state === "PENDING") {
                <form (ngSubmit)="decide('APPROVED')">
                  <label
                    >Your name<input
                      name="name"
                      [(ngModel)]="name"
                      required
                      maxlength="120"
                      autocomplete="name" /></label
                  ><label
                    >Notes or requested changes<textarea
                      name="comment"
                      [(ngModel)]="comment"
                      maxlength="2000"
                      rows="3"
                    ></textarea></label
                  ><label class="check"
                    ><input
                      type="checkbox"
                      name="confirm"
                      [(ngModel)]="confirmed"
                    />
                    I checked this version and its print specifications.</label
                  ><button [disabled]="busy() || !confirmed || !name.trim()">
                    Approve this version</button
                  ><button
                    class="secondary"
                    type="button"
                    [disabled]="busy() || !name.trim() || !comment.trim()"
                    (click)="decide('REVISION')"
                  >
                    Request a revision
                  </button>
                </form>
              } @else {
                <h2>
                  {{
                    p.state === "APPROVED"
                      ? "Approved. Ready for the shop."
                      : p.state === "REVISION"
                        ? "Changes requested."
                        : "A newer version is available."
                  }}
                </h2>
                <p>
                  {{
                    p.state === "SUPERSEDED"
                      ? "Ask the shop for your latest review link."
                      : "Your decision has been saved. You can safely close this page."
                  }}
                </p>
                @if (p.state === "APPROVED") {
                  <button (click)="print()">Print approval summary</button>
                }
              }
            </section>
          </div>
          @if (p.state === "APPROVED") {
            <section class="card receipt">
              <h2>Approval record</h2>
              <p>
                Version {{ p.number }} · {{ p.name }} ·
                {{ p.decided_at | date: "medium" }}
              </p>
              <p>{{ p.comment }}</p>
              <p class="hash">Image SHA-256: {{ p.digest }}</p>
              <p class="small">
                The specifications and preview above are the approved content.
              </p>
            </section>
          }
        } @else if (!error()) {
          <p>Loading your proof…</p>
        }
      } @else if (!logged()) {
        <section class="login card">
          <div class="eyebrow">SHOP WORKSPACE</div>
          <h1>Approve the proof.<br />Print with confidence.</h1>
          <p class="lead">
            One version. One decision. An approval record everyone can
            understand.
          </p>
          <form (ngSubmit)="login()">
            <label
              >Administrator password<input
                type="password"
                name="password"
                [(ngModel)]="password"
                required
                autocomplete="current-password" /></label
            ><button [disabled]="busy()">Open workspace</button>
          </form>
        </section>
      } @else if (!selected()) {
        <div class="eyebrow">SHOP WORKSPACE</div>
        <h1>Print jobs</h1>
        <p class="lead">Your production queue. Every artwork version, every customer decision.</p>
        <form class="card create" (ngSubmit)="create()">
          <label
            >New job title<input
              name="title"
              [(ngModel)]="title"
              required
              maxlength="120"
              placeholder="e.g. Olive Café · takeaway menus" /></label
          ><button [disabled]="busy() || !title.trim()">Create job</button>
        </form>
        <div class="jobs">
          @for (j of jobs(); track j.id) {
            <button class="job" (click)="open(j)">
              <span
                ><strong>{{ j.title }}</strong
                ><small>View artwork, specifications and decision →</small></span
              ><span class="badge" [attr.data-state]="j.state">{{ j.state || "NO PREVIEW" }}</span>
            </button>
          } @empty {
            <section class="card">
              <h2>Your first approval starts here.</h2>
              <p>
                Create a job, add its specifications and preview, then share a
                private review link.
              </p>
            </section>
          }
        </div>
      } @else {
        <button class="quiet" (click)="back()">← All jobs</button>
        <div class="eyebrow">JOB DETAILS</div>
        <h1>{{ selected()?.title }}</h1>
        <p class="lead">
          Every version keeps its own specifications and decision.
        </p>
        @if (!approved()) {
          <form class="card upload" (ngSubmit)="upload()">
            <h2>Add a proof version</h2>
            <label
              >Print specifications<textarea
                name="specs"
                [(ngModel)]="specs"
                required
                maxlength="2000"
                rows="3"
                placeholder="Size, stock, quantity, sides, finish and any important details"
              ></textarea></label
            ><label
              >JPG or PNG preview · up to 5 MB<input
                type="file"
                accept="image/jpeg,image/png"
                (change)="choose($event)"
                required /></label
            ><button [disabled]="busy() || !file || !specs.trim()">
              Save new version
            </button>
            <p class="small">
              A new version closes approval on earlier pending versions.
            </p>
          </form>
        }
        @if (share()) {
          <section class="card share">
            <h2>Private review link</h2>
            <p>
              Anyone with this link can view and decide on this proof. Share it
              only with your customer.
            </p>
            <input aria-label="Review link" readonly [value]="share()" /><button
              (click)="copy()"
            >
              {{ copied() ? "Copied" : "Copy link" }}
            </button>
          </section>
        }
        @for (v of versions(); track v.id) {
          <section
            class="card version"
            [class.unapproved]="v.state !== 'APPROVED'"
          >
            <div>
              <span class="badge" [attr.data-state]="v.state">Version {{ v.number }} · {{ v.state }}</span>
              <h2>Print specifications</h2>
              <p class="specs">{{ v.specs }}</p>
              <p>{{ v.name }} {{ v.decided_at | date: "medium" }}</p>
              <p>{{ v.comment }}</p>
              <p class="hash">SHA-256: {{ v.digest }}</p>
              @if (v.state === "PENDING") {
                <button [disabled]="busy()" (click)="link(v)">
                  Create / replace review link
                </button>
              }
              <button class="secondary" [disabled]="busy()" (click)="revoke(v)">
                Revoke review link
              </button>
              @if (v.state === "APPROVED") {
                <button (click)="print()">Print approval summary</button>
              }
            </div>
            <img
              [src]="'/api/admin/versions/' + v.id + '/image'"
              alt="Saved design preview"
            />
          </section>
        }
      }
      <footer>
        Print Approval System · Design approval, made unambiguous.
      </footer>
    </main>`,
})
class App {
  token = new URLSearchParams(location.hash.slice(1)).get("proof");
  logged = signal(false);
  busy = signal(false);
  error = signal("");
  jobs = signal<Job[]>([]);
  versions = signal<Proof[]>([]);
  selected = signal<Job | null>(null);
  proof = signal<Proof | null>(null);
  share = signal("");
  copied = signal(false);
  password = "";
  title = "";
  specs = "";
  name = "";
  comment = "";
  confirmed = false;
  file: File | null = null;
  constructor() {
    window.addEventListener("hashchange", () => location.reload());
    void this.refresh();
  }
  async api(path: string, method = "GET", body?: unknown): Promise<any> {
    let headers: Record<string, string> = {};
    if (method !== "GET") {
      const c = await fetch("/api/csrf", { cache: "no-store", signal: AbortSignal.timeout(15000) });
      headers["X-CSRF-TOKEN"] = (await c.json()).token;
    }
    if (
      body &&
      !(body instanceof FormData) &&
      !(body instanceof URLSearchParams)
    )
      headers["Content-Type"] = "application/json";
    const r = await fetch("/api" + path, {
      method,
      headers,
      signal: AbortSignal.timeout(15000),
      cache: "no-store",
      body:
        body instanceof FormData || body instanceof URLSearchParams
          ? body
          : body
            ? JSON.stringify(body)
            : undefined,
    });
    if (!r.ok)
      throw new Error(
        r.status === 401
          ? "Sign in with your shop administrator password."
          : r.status === 404
            ? "This link is unavailable. Ask the shop for a new review link."
            : r.status === 409
              ? "This proof has changed or was already decided. Refresh to see its current status."
              : r.status === 429
                ? "Too many requests. Wait one minute, then try again."
                : r.status === 507
                  ? "Shop storage is full. Contact the shop administrator before uploading again."
                  : r.status === 503
                    ? "The service is busy. Wait a moment, then try again."
                    : r.status === 413
                      ? "The preview is too large. Choose a JPG or PNG under 5 MB."
                      : "Unable to save. Check your details and image, then try again.",
      );
    return r.status === 204 || r.headers.get("content-length") === "0"
      ? null
      : await r.text().then((t) => (t ? JSON.parse(t) : null));
  }
  async run(fn: () => Promise<void>) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set("");
    try {
      await fn();
    } catch (e) {
      this.error.set(
        e instanceof TypeError || (e instanceof DOMException && e.name === "TimeoutError") ? "Connection failed. Check your connection and try again." : e instanceof Error ? e.message : "Connection failed. Try again.",
      );
    } finally {
      this.busy.set(false);
    }
  }
  async refresh() {
    await this.run(async () => {
      if (this.token) {
        this.proof.set(await this.api("/proof/" + this.token));
      } else {
        try {
          this.jobs.set(await this.api("/admin/jobs"));
          this.logged.set(true);
          if (this.selected())
            this.versions.set(
              await this.api(
                "/admin/jobs/" + this.selected()!.id + "/versions",
              ),
            );
        } catch (e) {
          if (this.logged()) throw e;
        }
      }
    });
  }
  async login() {
    await this.run(async () => {
      await this.api(
        "/login",
        "POST",
        new URLSearchParams({ username: "admin", password: this.password }),
      );
      this.password = "";
      this.jobs.set(await this.api("/admin/jobs"));
      this.logged.set(true);
    });
  }
  async logout() {
    await this.run(async () => {
      await this.api("/logout", "POST");
      this.logged.set(false);
      this.selected.set(null);
      this.share.set("");
    });
  }
  async create() {
    await this.run(async () => {
      const j = await this.api("/admin/jobs", "POST", { title: this.title });
      this.selected.set({ id: j.id, title: this.title, state: null });
      this.versions.set([]);
      this.title = "";
    });
  }
  async back() {
    this.selected.set(null);
    await this.refresh();
  }
  async open(j: Job) {
    await this.run(async () => {
      this.versions.set(await this.api("/admin/jobs/" + j.id + "/versions"));
      this.selected.set(j);
      this.share.set("");
    });
  }
  choose(e: Event) {
    this.file = (e.target as HTMLInputElement).files?.[0] || null;
  }
  approved() {
    return this.versions().some((v) => v.state === "APPROVED");
  }
  async upload() {
    await this.run(async () => {
      const f = new FormData();
      f.append("specs", this.specs);
      f.append("image", this.file!);
      await this.api(
        "/admin/jobs/" + this.selected()!.id + "/versions",
        "POST",
        f,
      );
      this.versions.set(
        await this.api("/admin/jobs/" + this.selected()!.id + "/versions"),
      );
      this.share.set("");
    });
  }
  async link(v: Proof) {
    await this.run(async () => {
      const r = await this.api("/admin/versions/" + v.id + "/share", "POST");
      this.share.set(location.origin + "/#proof=" + r.token);
      this.copied.set(false);
    });
  }
  async revoke(v: Proof) {
    await this.run(async () => {
      await this.api("/admin/versions/" + v.id + "/share", "DELETE");
      this.share.set("");
    });
  }
  async copy() {
    await this.run(async () => {
      await navigator.clipboard.writeText(this.share());
      this.copied.set(true);
    });
  }
  async decide(action: string) {
    await this.run(async () => {
      this.proof.set(
        await this.api("/proof/" + this.token + "/decision", "POST", {
          version: this.proof()!.id,
          action,
          name: this.name,
          comment: this.comment,
        }),
      );
    });
  }
  print() {
    window.print();
  }
}
bootstrapApplication(App).catch(console.error);
