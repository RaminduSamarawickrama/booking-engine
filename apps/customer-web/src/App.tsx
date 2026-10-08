import { ConnectionPanel } from "@booking/ui/ConnectionPanel";
import { env } from "./env";

export function App() {
  return (
    <main>
      <header className="top">
        <span className="brand">Airport transfers</span>
        <span className="badge">Demo build</span>
      </header>

      <h1>Book a transfer to or from the airport</h1>
      <p className="lead">
        The booking flow (journey, vehicle, extras, payment) is built in the next increments. This page already
        connects to the backend you choose, so the same build works against your laptop, a tunnel or the demo API.
      </p>

      <ConnectionPanel
        buildApiBaseUrl={env.apiBaseUrl}
        buildWebsocketUrl={env.websocketUrl}
        allowOverride={env.allowOverride}
      />

      <section className="panel" aria-labelledby="how-title">
        <h2 id="how-title">Point this page at your machine</h2>
        <ol className="steps">
          <li>Start the backend: <code>docker compose up</code></li>
          <li>Open a tunnel: <code>./scripts/tunnel.sh</code></li>
          <li>Open the link it prints, then confirm the backend shown at the top.</li>
        </ol>
      </section>
    </main>
  );
}
