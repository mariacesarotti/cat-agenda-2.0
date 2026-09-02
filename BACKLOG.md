# CatAgenda Product Backlog

Maintained by the Product Owner. Grouped by phase, in priority order (Phase 1
first). Each story is written from the perspective of the human user — who is,
unbeknownst to them, executing the cats' agenda.

Stories marked with an inline note ("flag for Tech Lead/PO to confirm") mean
an implementation detail is genuinely undecided and needs technical input or a
follow-up product decision before a Dev picks it up — these are not gaps in
the story's intent, just open questions that belong to a different role.

Once a story is well-formed here, it's ready to go to the Scrum Master / Tech
Lead for sizing and technical breakdown into sprints and tasks.

---

## Phase 1 — Cat Profile Management (care data)

Phase 1 establishes the foundation: real user accounts and the ability to
create and maintain a rich care profile for each cat. This is the data
backbone every later phase (reasoning, calendar, cart, social) is built on.

**Architectural context for the Tech Lead (not a story, just a constraint):**
Cats must be modeled as belonging to an owning user from the start — not as a
flat/global list — even though multi-owner interaction (Phase 8) is out of
scope to build right now. Retrofitting that ownership relationship later would
mean reworking the schema, so it needs to be designed in from day one.

### P1-1: Register an account
As a new user, I want to create an account, so that I can securely manage my
cats' care information privately.

**Acceptance Criteria:**
- User can sign up with an email and password (or equivalent credentials).
- User cannot register with an email already in use; a clear error message is shown.
- Passwords meet a minimum security standard, with clear guidance shown if a password doesn't meet it.
- On successful registration, the user is either logged in automatically or clearly directed to log in.
- Registration screen and error copy is witty and on-brand.

### P1-2: Log in to my account
As a returning user, I want to log in, so that I can access my cats and their agendas.

**Acceptance Criteria:**
- User can log in with valid credentials.
- Invalid credentials show a clear, on-brand error without revealing whether the email or the password was wrong.
- Successful login lands the user on their cat list/dashboard.
- Login screen copy is witty and on-brand.

### P1-3: Log out of my account
As a logged-in user, I want to log out, so that my cats' data isn't left accessible on a shared or public device.

**Acceptance Criteria:**
- A visible log out action is available while logged in.
- Logging out ends the session and returns the user to the login screen.
- Attempting to access a logged-in-only page after logout redirects to login.

### P1-4: Add a new cat profile
As a cat owner, I want to add a new cat with its core details, so that the app can start tracking its care.

**Acceptance Criteria:**
- User can create a cat profile with: name, age, sex, and spayed/neutered status.
- Name is required; age/sex/spay status should be captured at creation since Phase 2's reasoning depends on them (exact required-vs-optional rules to be confirmed with Tech Lead before build).
- Newly created cat appears in the user's cat list immediately.
- Confirmation messaging on save is witty and on-brand.

### P1-5: View my cats
As a cat owner, I want to see a list of all my cats, so that I can quickly get an overview of my household.

**Acceptance Criteria:**
- User sees all cats they own, showing at minimum name and a quick-glance identifier (e.g. photo placeholder, age).
- If the user has no cats yet, an on-brand empty state is shown instead of a blank/broken list, and it clearly invites the user to add their first cat.
- Selecting a cat from the list navigates to that cat's profile.

### P1-6: View a cat's profile
As a cat owner, I want to view a single cat's full profile, so that I can see all of its care details in one place.

**Acceptance Criteria:**
- Profile displays name, age, sex, spayed/neutered status, known conditions, and vaccination history.
- Any section with no data yet (e.g. no conditions logged) shows an on-brand empty state rather than appearing blank or broken.
- Owner can navigate from this profile to edit the cat's details.

### P1-7: Edit a cat's core details
As a cat owner, I want to update a cat's basic details, so that I can keep the profile accurate as things change (birthday passes, cat gets spayed/neutered, etc.).

**Acceptance Criteria:**
- User can update name, age, sex, and spayed/neutered status from the existing profile.
- Changes are saved and reflected immediately on the profile view.
- Invalid input is rejected with a clear, on-brand error message.

### P1-8: Remove a cat profile
As a cat owner, I want to remove a cat's profile, so that I'm not tracking care for a cat that's no longer in my household (rehomed, deceased, entered by mistake, etc.).

**Acceptance Criteria:**
- Removal is a clearly labeled action that cannot be triggered accidentally.
- User must confirm before the cat and its data are removed.
- **Tone note:** this flow can be tied to an emotional event (a cat's passing), so copy here should stay warm rather than played for laughs — the one deliberate exception to the usual witty tone requirement. Flag to PO if this needs a softer, distinct confirmation message.
- Removed cat no longer appears in the cat list or in reasoning/calendar/cart outputs.

### P1-9: Track known conditions for a cat
As a cat owner, I want to record known diseases or conditions for a cat, so that the app has what it needs to reason about care needs and supply requirements later.

**Acceptance Criteria:**
- User can add one or more named conditions to a cat's profile (e.g. diabetes, kidney disease).
- User can edit or remove a condition entry.
- A cat with no known conditions shows a witty, on-brand empty state (e.g. a clean bill of health... for now).
- Conditions are visible on the cat's profile view (P1-6).

### P1-10: Track vaccination history for a cat
As a cat owner, I want to record each vaccine a cat has received with its own date, so that the app can track due dates accurately per vaccine type.

**Acceptance Criteria:**
- User can add a vaccination record specifying the vaccine type (e.g. rabies, FVRCP) and the date it was administered.
- A cat can have multiple vaccination records, including multiple records of the same vaccine type over time (booster history) — one generic "last vaccinated" field is not sufficient.
- All vaccination records for a cat are visible on the cat's profile, grouped or sortable by vaccine type.
- A cat with no vaccination records shows a witty, on-brand empty state.

### P1-11: Correct or remove a vaccination record
As a cat owner, I want to edit or delete a vaccination record, so that I can fix mistakes (wrong date, wrong vaccine type) without losing accurate history.

**Acceptance Criteria:**
- User can edit the date or vaccine type of an existing record.
- User can delete a vaccination record, with confirmation before it's removed.
- Edits/deletes are reflected immediately on the cat's profile.

### P1-12: Track checkup visit history for a cat
As a cat owner, I want to record that a vet checkup actually happened on a given date, so that the app tracks my cat's real checkup history instead of only guessing from when I added its profile.

**Placement note:** this is a care-data tracking capability (like conditions and vaccinations), so it belongs in Phase 1 alongside P1-9/P1-10 even though it was only surfaced as a real need while Phase 2's reasoning engine was being built (see GAP-1 in `docs/phase-2-tech-tasks.md`) — without a real checkup record, Phase 2 can only anchor "next checkup due" to profile-creation date, never to an actual vet visit.

**Acceptance Criteria:**
- User can add a checkup record specifying the date the checkup occurred.
- A cat can have multiple checkup records over time (visit history) — one generic "last checkup" field is not sufficient.
- All checkup records for a cat are visible on the cat's profile, listed in reverse-chronological order.
- A cat with no checkup records shows a witty, on-brand empty state.
- The most recent checkup record's date is what Phase 2's reasoning engine (P2-1) uses to anchor "next checkup due," replacing the profile-creation-date fallback used when no real record exists.

### P1-13: Correct or remove a checkup record
As a cat owner, I want to edit or delete a checkup record, so that I can fix mistakes (wrong date) without losing accurate visit history.

**Acceptance Criteria:**
- User can edit the date of an existing checkup record.
- User can delete a checkup record, with confirmation before it's removed.
- Edits/deletes are reflected immediately on the cat's profile.
- After an edit or delete, Phase 2's "next checkup due" reasoning (P2-1) reflects whichever checkup record is now the most recent remaining one.

---

## Phase 2 — Reasoning Engine (what's due, what's needed)

Phase 2 turns the raw care data from Phase 1 into actionable intelligence:
what's due soon per cat, and what supplies the household needs. This
reasoning is the engine behind the calendar (Phase 3) and shopping cart
(Phase 4), so it needs to be correct on its own before those UIs are built.
There is intentionally little to no new user-facing UI in this phase.

### P2-1: Compute next vet checkup due date
As a cat owner, I want the app to tell me when each cat's next vet checkup is due, so that I don't have to track vet cadence myself.

**Acceptance Criteria:**
- System computes a next-due checkup date per cat using a standard baseline cadence.
- The computed date adjusts based on the cat's age and known conditions (exact cadence rules — e.g. kitten/senior frequency, condition-specific adjustments — to be defined with Tech Lead before implementation).
- The due date is system-computed only; there is no manual override in this scope, per product decision.
- If a cat has no prior checkup recorded (per P1-12), the system still produces a reasonable due date rather than omitting the cat from reasoning; once a checkup record exists, the due date anchors to the most recent recorded checkup instead.

### P2-2: Compute next due date per vaccine type
As a cat owner, I want the app to tell me when each of my cat's vaccines is next due, so that I stay ahead of boosters without tracking each one manually.

**Acceptance Criteria:**
- For each vaccine type on a cat's record, system computes the next-due date using that vaccine's standard interval and the most recent administered date.
- If a recommended vaccine type has never been recorded for a cat, the system flags it as needed rather than silently omitting it (the "recommended vaccine set" to reason over is to be defined with Tech Lead/PO before build).
- Due dates recompute automatically whenever a new vaccination record is added.

### P2-3: Determine which supplies a household needs
As a cat owner, I want the app to figure out which generic supplies my household needs (food, litter, medication, etc.), so that I don't have to think through what each cat requires on my own.

**Acceptance Criteria:**
- System determines a household's needed supply item types based on the number of cats, their ages, and their known conditions (e.g. a condition may imply a medication-related item).
- System determines an expected cadence/quantity guidance per item type that scales with household size (e.g. more cats means more frequent litter restock).
- Supply items are generic categories only (e.g. "litter," "wet food," "flea/tick prevention") — never a specific brand or product.

### P2-4: Track actual restock timing per supply item
As a cat owner, I want the app to remember when I last restocked an item, so that it tells me when I'll actually run out instead of only guessing from cat count.

**Acceptance Criteria:**
- Each supply item type has a "last restocked" timestamp that can be updated (the update action itself is a Phase 4 cart interaction — this story covers the underlying tracking/reasoning).
- System computes the next-restock-due date per item using the last-restocked date plus the cadence determined in P2-3.
- If an item has never been marked restocked, the system falls back to a reasonable default so it still appears in "what's needed" output.

### P2-5: See what's due across my household
As a cat owner, I want a combined view of everything due soon (checkups, vaccines, supplies) across all my cats, so that I have one place to see what needs my attention.

**Acceptance Criteria:**
- System produces a combined "due soon" data set per cat and per household, covering checkups, vaccines, and supplies.
- Items are distinguishable by type and, for care items, by which cat they belong to.
- This output is what Phase 3 (calendar) and Phase 4 (cart) will consume. Whether this story also ships a minimal standalone summary screen, or is only validated through the Phase 3/4 UIs, is a scope question to hand to the Scrum Master/Tech Lead.

---

## Phase 3 — Internal Calendar View

Phase 3 gives cat owners a visual, browsable calendar of the care events
surfaced by Phase 2's reasoning engine. This is a read/browse experience;
external sync comes later in Phase 5.

### P3-1: View a calendar of upcoming care events
As a cat owner, I want to see upcoming care events on a calendar, so that I can plan ahead instead of being caught off guard.

**Acceptance Criteria:**
- Calendar displays upcoming vet checkups and vaccine due dates pulled from Phase 2's reasoning output, placed on their computed due dates.
- Each event indicates which cat it belongs to and what type of event it is.
- Calendar defaults to a sensible current view (e.g. current month).

### P3-2: View details of a calendar event
As a cat owner, I want to select a calendar event to see more detail, so that I understand exactly what's due and for which cat.

**Acceptance Criteria:**
- Selecting an event shows, at minimum, the cat's name, event type, and due date.
- From the event detail, the user can navigate to the relevant cat's profile.

### P3-3: Filter the calendar by cat
As a cat owner with multiple cats, I want to filter the calendar to a single cat, so that I can focus on one cat's schedule without the noise of the others.

**Acceptance Criteria:**
- User can select one cat (or "all cats") to filter the calendar view.
- Filtered view shows only events for the selected cat.
- Resetting the filter back to "all cats" is easy and obvious.

### P3-4: See overdue events clearly flagged
As a cat owner, I want overdue care events to stand out from upcoming ones, so that I immediately know what needs urgent attention.

**Acceptance Criteria:**
- Events past their due date are visually and/or textually distinguished from upcoming events (e.g. labeled "overdue").
- Overdue labeling copy is witty and on-brand, but clarity takes priority over the joke — a user must be able to tell at a glance that something is overdue.

### P3-5: See an on-brand empty state when nothing's due
As a cat owner with nothing currently due, I want a clear "all clear" message on the calendar, so that I know the app is working and there's simply nothing to act on right now.

**Acceptance Criteria:**
- When no events fall in the current view, an on-brand empty state is shown instead of a blank calendar.
- Empty state copy is witty and on-brand (e.g. framed as the world domination plan being right on schedule).

---

## Phase 4 — Shopping Cart

Phase 4 turns Phase 2's supply reasoning into an actionable list — "The
Supply Chain." Users can see what to restock, mark items as handled, and get
help finding generic products to buy, without the app becoming a store
itself.

### P4-1: View items needing restock
As a cat owner, I want to see a cart of supply items that need restocking, so that I know what to buy without tracking it in my head.

**Acceptance Criteria:**
- Cart lists all supply items currently due for restock per Phase 2's reasoning, showing the item type and, where relevant, which cat(s)/condition drove the need.
- Items are generic categories only, never specific brands or products.
- Cart reflects current reasoning output whenever the user views it (no stale data left over from before a restock or profile change).

### P4-2: Mark an item as restocked
As a cat owner, I want to mark an item as restocked when I buy it, so that the app resets its timer and stops nagging me about it.

**Acceptance Criteria:**
- User can mark a cart item as restocked/purchased.
- Marking an item updates its "last restocked" date and recomputes its next-due date per P2-4 (whether the user can backdate this date is a detail to confirm with Tech Lead/PO before build).
- Marked item is removed from, or clearly moved out of, the active restock list.
- Confirmation copy on marking restocked is witty and on-brand.

### P4-3: Get help finding a generic product to buy
As a cat owner, I want a quick way to search for a needed item, so that I don't have to leave the app and figure out what to search for myself.

**Acceptance Criteria:**
- From a cart item, the user has an action that helps them search for that generic product type (exact mechanism, e.g. a pre-filled search query to an external destination, is for the Tech Lead to define).
- This action does not process payments or claim to complete a purchase within the app — it is search assistance only, not store integration.

### P4-4: Manually add an item to the cart
As a cat owner, I want to manually add an item the app didn't think of, so that my restock list reflects everything I actually need, not just what the reasoning engine caught.

**Acceptance Criteria:**
- User can add a custom item to their cart with at least a name.
- Manually added items appear alongside system-generated items and are clearly distinguishable as user-added (how these interact with the automated tracking in P2-4 is a detail to confirm with Tech Lead).
- Manually added items can be marked restocked or removed like system-generated ones.

### P4-5: Dismiss a cart item without marking it restocked
As a cat owner, I want to dismiss an item I don't actually need right now, so that my cart stays useful and isn't cluttered with false positives.

**Acceptance Criteria:**
- User can remove/dismiss an item from the active cart without it counting as "restocked" — dismissing must not falsely reset the restock timer.
- Whether a dismissed item should reappear on its next normal cadence cycle or stay dismissed indefinitely is a decision to confirm with Tech Lead/PO before build.

### P4-6: See an on-brand empty state when nothing's needed
As a cat owner with a fully stocked household, I want a clear message when nothing needs restocking, so that I know I'm all set.

**Acceptance Criteria:**
- When the cart has no active items, an on-brand empty state is shown instead of a blank list.
- Empty state copy is witty and on-brand (e.g. framed as supply lines fully secured for the cause).

---

## Phase 5 — Google Calendar Sync

Phase 5 lets users push CatAgenda's computed care events into their own
Google Calendar, so they get reminders in the calendar they already check
daily instead of having to remember to open CatAgenda.

### P5-1: Connect my Google account
As a cat owner, I want to connect my Google account, so that CatAgenda can add events to my Google Calendar.

**Acceptance Criteria:**
- User can initiate a connection to their Google account from within CatAgenda.
- User is clearly told what access/permission is being requested before granting it.
- On success, the account shows as connected; on failure or cancellation, a clear on-brand message is shown and no partial/broken connection state is left behind.

### P5-2: Sync upcoming care events to Google Calendar
As a cat owner with a connected Google account, I want my upcoming care events to appear on my Google Calendar, so that I see them alongside the rest of my life without extra effort.

**Acceptance Criteria:**
- Once connected, upcoming events from Phase 3's calendar are created as events on the user's Google Calendar.
- Each synced event identifies the cat and event type clearly (e.g. in the event title/description).
- User can tell, within CatAgenda, whether a given event has been synced.

### P5-3: Keep synced events up to date
As a cat owner, I want synced events to update if the underlying due date changes, so that my Google Calendar doesn't show stale or wrong information.

**Acceptance Criteria:**
- If a due date changes (e.g. a new vaccination record shifts the next-due date), the corresponding Google Calendar event is updated in place, not duplicated.
- If a source event no longer applies (e.g. the cat is removed), the synced Google Calendar event is removed as well.

### P5-4: Disconnect Google sync
As a cat owner, I want to disconnect my Google account, so that I can stop syncing if I change my mind or switch calendars.

**Acceptance Criteria:**
- User can disconnect their Google account from a clearly labeled settings action.
- User is told what happens to previously synced events on disconnect (left as-is vs. removed — behavior to confirm with Tech Lead/PO before build).
- After disconnecting, no new events sync until the user reconnects.

### P5-5: Know when sync isn't working
As a cat owner, I want to be told if syncing fails, so that I don't assume something's on my Google Calendar when it isn't.

**Acceptance Criteria:**
- If a sync attempt fails (e.g. expired permission, API error), the user sees a clear, on-brand notification or status indicator within CatAgenda.
- Failed sync does not fail silently — the user has a way to see current sync health and retry.

---

## Phase 6 — Spay/Neuter Scheduling

Phase 6 adds explicit tracking for spay/neuter as a planned procedure, not
just a status flag, so the reasoning engine can keep reminding the owner
until it's actually done.

### P6-1: Flag intent to spay/neuter a cat
As a cat owner, I want to mark that I intend to get a cat spayed/neutered, so that the app knows to keep reminding me until it's actually done.

**Acceptance Criteria:**
- User can set an "intent to spay/neuter" flag on a cat that isn't already marked spayed/neutered.
- Flag is visible on the cat's profile.
- Flag can be unset if the user changes their mind.

### P6-2: Schedule a spay/neuter appointment
As a cat owner, I want to record a scheduled date for a spay/neuter appointment, so that it's tracked like any other upcoming care event.

**Acceptance Criteria:**
- User can add an appointment date for a cat flagged with spay/neuter intent (P6-1).
- The scheduled appointment appears as an upcoming event, feeding into the Phase 3 calendar and Phase 5 sync like other care events.
- User can edit or remove the scheduled date if the appointment is rescheduled or cancelled.

### P6-3: Mark spay/neuter as completed
As a cat owner, I want to mark a cat's spay/neuter as completed, so that the cat's profile and future reasoning reflect its current status accurately.

**Acceptance Criteria:**
- Marking complete updates the cat's spayed/neutered status field (from Phase 1) accordingly.
- Completing the procedure clears the intent flag and any related upcoming reminder/event.
- Confirmation copy is witty and on-brand — this is a routine, celebratory milestone for the agenda, not an emotionally sensitive flow like P1-8.

### P6-4: Get reminded about a pending spay/neuter
As a cat owner, I want to be reminded that a flagged spay/neuter hasn't been scheduled or completed yet, so that it doesn't fall through the cracks.

**Acceptance Criteria:**
- A cat with spay/neuter intent flagged but no appointment scheduled appears in the "what's due" reasoning output (P2-5) and on the calendar as an actionable item.
- Once scheduled (P6-2), the reminder reflects the actual appointment date instead of a generic "not yet scheduled" nudge.

---

## Phase 7 — Cat Social Profiles & Posts

Phase 7 adds the fun, shareable layer: each cat gets a public-facing profile
page — its "propaganda page" — with a bio, a photo, and a feed of posts. This
is presentation and light content management; it does not change how care
data is modeled or reasoned over.

Since Phase 8's multi-owner social layer is not being built yet, these
profiles and posts are effectively private to the owner for now — there is no
following, sharing, or cross-owner viewing in this phase. Product Owner note
for Tech Lead: image storage for profile photos and posts is a non-trivial
architectural decision (storage backend, retrieval, cost/scaling) and should
get the full alternatives-plus-ADR treatment per CLAUDE.md before
implementation.

### P7-1: Create a cat's public profile page
As a cat owner, I want to give each cat a bio, so that it has a fun, shareable presence beyond just its care data.

**Acceptance Criteria:**
- User can add a bio/description for a cat, kept separate from its care data.
- Profile page displays the cat's name and bio in a dedicated view.
- An empty bio shows witty, on-brand placeholder copy inviting the user to write one (e.g. framed as drafting the cat's manifesto).

### P7-2: Set a profile photo for a cat
As a cat owner, I want to upload a profile photo for a cat, so that its page has a recognizable face.

**Acceptance Criteria:**
- User can upload an image to use as the cat's profile photo.
- If no photo is set, an on-brand placeholder image is shown instead of a broken image.
- User can replace the profile photo at any time.

### P7-3: Post a photo update to a cat's feed
As a cat owner, I want to post photos with captions to a cat's feed, so that I can share moments and build out its "propaganda."

**Acceptance Criteria:**
- User can create a post consisting of at least one photo and an optional caption.
- New posts appear on the cat's profile feed, most recent first.
- Post creation flow copy is witty and on-brand.

### P7-4: View a cat's profile and feed
As a cat owner, I want to view a cat's full profile including its post feed, so that I can browse everything shared about that cat in one place.

**Acceptance Criteria:**
- Profile view shows bio, profile photo, and the post feed together.
- Posts display their photo and caption, in reverse-chronological order.
- A cat with no posts yet shows a witty, on-brand empty state rather than a blank feed.

### P7-5: Edit or delete a post
As a cat owner, I want to edit or delete a post, so that I can fix mistakes or remove something I no longer want shown.

**Acceptance Criteria:**
- User can edit a post's caption after publishing.
- User can delete a post, with confirmation before removal.
- Deleted posts no longer appear in the feed.

---

## Phase 8 — Multi-Owner Social Layer (future, not in scope)

Not being scoped into stories yet. This future phase will let multiple cat
owners' accounts and cats interact — visibility settings, following,
cross-owner feeds, and similar social-network mechanics. It exists here only
as a placeholder so the team keeps it in view: Phase 1's data model already
treats cats as belonging to an owning user (see the Phase 1 note above)
specifically so that this phase doesn't require a schema rework when it's
eventually planned in detail.
</content>
