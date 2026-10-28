import type { MouseEvent } from 'react'
import logoIcon from '../../assets/img/logo-icon.svg'
import './site.css'

interface Props {
  signedIn: boolean
}

/** Scrolls to a section of this page. The normal "#section" links cannot be used: the address bar is the router. */
function scrollToSection(event: MouseEvent<HTMLAnchorElement>, id: string) {
  event.preventDefault()
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById(id)?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth' })
}

/** The public home page of Hugo's Appliance Repair (the same content as the company website). */
export function HomePage({ signedIn }: Props) {
  return (
    <div className="site">
      <div className="utility">
        <div className="wrap">
          <span>Open 7 Days • 8:30 AM – 6:30 PM</span>
          <span>Serving Sydney &amp; CBRM</span>
        </div>
      </div>
      <header>
        <div className="wrap nav">
          <a className="brand" href="#/" aria-label="Hugo's Appliance Repair home">
            <img className="mark" src={logoIcon} width={58} height={58} alt="" decoding="async" />
            <div className="wordmark">
              <strong>HUGO'S</strong>
              <span>APPLIANCE REPAIR</span>
            </div>
          </a>
          <nav className="navlinks">
            <a href="#/" onClick={(event) => scrollToSection(event, 'services')}>Services</a>
            <a href="#/" onClick={(event) => scrollToSection(event, 'why')}>Why Hugo's</a>
            <a href="#/" onClick={(event) => scrollToSection(event, 'contact')}>Contact</a>
            <a className="btn outline" href="sms:+19029801777">Text Us</a>
            <a className="btn primary" href="tel:+19029801777">902-980-1777</a>
          </nav>
          <a className="btn outline loginlink" href={signedIn ? '#/my/customers' : '#/login'}>
            {signedIn ? 'My account' : 'Staff login'}
          </a>
        </div>
      </header>
      <main>
        <section className="hero">
          <div className="wrap hero-grid">
            <div>
              <div className="eyebrow">Appliance Repair • Sydney &amp; CBRM</div>
              <h1>Keeping Your Home Running Smoothly.</h1>
              <p className="lead">
                Professional appliance diagnostics, repair, and maintenance built around systematic testing, clear
                solutions, and dependable workmanship.
              </p>
              <div className="actions">
                <a className="btn primary" href="tel:+19029801777">Call 902-980-1777</a>
                <a className="btn green" href="sms:+19029801777">Text Your Appliance Issue</a>
              </div>
              <div className="proof">
                <span>Precision Diagnostics</span>
                <span>Upfront Pricing</span>
                <span>Professional Service</span>
              </div>
            </div>
            <aside className="panel">
              <h2>Diagnose. Repair. Verify.</h2>
              <p>A disciplined service process — not blind parts swapping.</p>
              <div className="process">
                <div className="step">
                  <div className="num">1</div>
                  <div><b>Diagnose</b><small>Test symptoms, circuits and components.</small></div>
                </div>
                <div className="step">
                  <div className="num">2</div>
                  <div><b>Explain</b><small>Clear findings, options and pricing.</small></div>
                </div>
                <div className="step">
                  <div className="num">3</div>
                  <div><b>Repair &amp; Verify</b><small>Complete the work and confirm operation.</small></div>
                </div>
              </div>
            </aside>
          </div>
        </section>

        <section className="services" id="services">
          <div className="wrap">
            <div className="section-head">
              <h2>Repair, diagnostics &amp; maintenance.</h2>
              <p>Practical field service for the appliances and systems that keep your home running.</p>
            </div>
            <div className="service-grid">
              <div className="service">
                <h3>Repair &amp; Diagnostics</h3>
                <ul>
                  <li>Washer &amp; Dryer</li>
                  <li>Refrigerator &amp; Freezer</li>
                  <li>Stove, Range &amp; Oven</li>
                  <li>Dishwasher</li>
                  <li>Electrical Diagnostics &amp; Component Testing</li>
                </ul>
              </div>
              <div className="service">
                <h3>Cleaning &amp; Maintenance</h3>
                <ul>
                  <li>Heat Pump Deep Cleaning</li>
                  <li>Washer &amp; Dryer Deep Cleaning</li>
                  <li>Dryer Vent Inspection &amp; Cleaning</li>
                  <li>Preventive Maintenance</li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <section id="why">
          <div className="wrap">
            <div className="section-head">
              <h2>Professional service starts with the right diagnosis.</h2>
              <p>
                The service system is simple: understand the equipment, identify the fault, make the right repair, and
                verify the result.
              </p>
            </div>
            <div className="grid3">
              <div className="card">
                <div className="bar"></div>
                <h3>Accurate Diagnosis</h3>
                <p>Systematic electrical and component testing before recommending parts or repairs.</p>
              </div>
              <div className="card">
                <div className="bar"></div>
                <h3>Clear Pricing</h3>
                <p>Repair options and pricing are explained before approved work begins.</p>
              </div>
              <div className="card">
                <div className="bar"></div>
                <h3>Professional Workmanship</h3>
                <p>Careful, clean service with clear communication from diagnosis through completion.</p>
              </div>
            </div>
          </div>
        </section>

        <section className="system">
          <div className="wrap">
            <div className="section-head">
              <h2>Built around systems, not guesswork.</h2>
              <p>A technical service philosophy designed to produce consistent, understandable and verifiable results.</p>
            </div>
            <div className="principles">
              <div className="principle">
                <h3>Systematic</h3>
                <p>Follow evidence and testing rather than assumptions.</p>
              </div>
              <div className="principle">
                <h3>Transparent</h3>
                <p>Make the problem, options and pricing understandable.</p>
              </div>
              <div className="principle">
                <h3>Verified</h3>
                <p>A repair is not finished until operation is checked.</p>
              </div>
            </div>
          </div>
        </section>

        <section id="contact">
          <div className="wrap contact">
            <div>
              <div className="eyebrow">Local Service</div>
              <h2>Need an appliance checked?</h2>
              <p>
                Send the appliance type, brand, model number if available, and a short description of the problem.
              </p>
              <div className="actions">
                <a className="btn primary" href="tel:+19029801777">Call Now</a>
                <a className="btn green" href="sms:+19029801777">Text Us</a>
              </div>
            </div>
            <div className="info">
              <p>
                <b>Business Hours</b>
                <br />
                Monday – Sunday
                <br />
                8:30 AM – 6:30 PM
              </p>
              <p>
                <b>Contact</b>
                <br />
                <a href="tel:+19029801777">902-980-1777</a>
                <br />
                <a href="mailto:service@hugoappliance.com">service@hugoappliance.com</a>
              </p>
              <p>
                <b>Service Area</b>
                <br />
                Sydney, Glace Bay, North Sydney, Sydney Mines, New Waterford &amp; surrounding CBRM communities.
              </p>
            </div>
          </div>
        </section>
      </main>
      <footer>
        <div className="wrap foot">
          <div>
            <strong>HUGO'S APPLIANCE REPAIR</strong>
            <br />
            Keeping Your Home Running Smoothly.
          </div>
          <div>© 2026 Hugo’s Appliance Repair. All rights reserved.</div>
        </div>
      </footer>
      <div className="mobilebar">
        <a className="btn primary" href="tel:+19029801777">Call Now</a>
        <a className="btn green" href="sms:+19029801777">Text Us</a>
      </div>
    </div>
  )
}
