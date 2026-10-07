-- ============================================================================
-- QUICKY SEED DATA (v3.1) — demo profiles to swipe + seeded chats
-- ============================================================================
--
-- WHAT THIS SEEDS
--   1. 15 dummy auth users + full discovery profiles (9 women, 6 men,
--      ages 22-31, verified/online mix, real remote photos) so the
--      Discover deck has cards to swipe through.
--   2. user_interests mirror rows (matching + shared-interest scoring).
--   3. "dummy liked you" rows — liking those 6 profiles instantly MATCHes.
--   4. For EVERY existing real account: 5 ready-made conversations with
--      message history + unread badges, so the Chats tab (and the chat
--      banner ad / discovery native ad) can be tested right after login.
--
-- HOW TO RUN
--   Supabase Dashboard → SQL Editor → paste → RUN. (Runs as postgres /
--   service role, so RLS does not block the seed.)
--
-- IDEMPOTENT
--   Safe to re-run: auth users + profiles upsert, likes/matches use ON
--   CONFLICT guards, and message history is only inserted when the
--   conversation is still empty.
--
-- SEED ACCOUNTS
--   Emails end in @quicky.seed (invalid TLD — they cannot receive mail).
--   Password for every seed account:  password     (dev-only, see cleanup).
--
-- CLEANUP — run these to remove everything this file created:
--   delete from public.likes         where user_id::text like '5eed0001-%' or target_user_id::text like '5eed0001-%';
--   delete from public.user_interests where user_id::text like '5eed0001-%';
--   delete from public.matches       where user_a_id::text like '5eed0001-%' or user_b_id::text like '5eed0001-%';
--   delete from public.messages     where conversation_id like 'match_5eed0001-%';
--   delete from auth.users          where email like '%@quicky.seed';   -- cascades profiles
-- ============================================================================


-- ============================================================================
-- 1. DUMMY AUTH USERS (fixed UUIDs 5eed0001-…-01 … -15)
-- ============================================================================

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, created_at, updated_at,
    raw_app_meta_data, raw_user_meta_data,
    confirmation_token, recovery_token, email_change, email_change_token_new
)
values
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'sarah.mathews@quicky.seed', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'priya.nair@quicky.seed',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'ananya.rao@quicky.seed',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000004', 'authenticated', 'authenticated', 'meera.pillai@quicky.seed',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000005', 'authenticated', 'authenticated', 'diya.sharma@quicky.seed',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000006', 'authenticated', 'authenticated', 'riya.kapoor@quicky.seed',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000007', 'authenticated', 'authenticated', 'tara.menon@quicky.seed',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000008', 'authenticated', 'authenticated', 'kavya.iyer@quicky.seed',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-000000000009', 'authenticated', 'authenticated', 'nithya.bhat@quicky.seed',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000a', 'authenticated', 'authenticated', 'arjun.nair@quicky.seed',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000b', 'authenticated', 'authenticated', 'rahul.verma@quicky.seed',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000c', 'authenticated', 'authenticated', 'aditya.rao@quicky.seed',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000d', 'authenticated', 'authenticated', 'karthik.menon@quicky.seed',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000e', 'authenticated', 'authenticated', 'vikram.shetty@quicky.seed',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '5eed0001-0000-4000-8000-00000000000f', 'authenticated', 'authenticated', 'dev.patel@quicky.seed',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', now(), now() - interval '30 days', now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb, '', '', '', '')
on conflict (id) do nothing;


-- ============================================================================
-- 2. DISCOVERY PROFILES (the handle_new_user trigger already created bare
--    rows — this upsert fills every display field; onboarding_completed
--    = true so get_discovery_profiles serves them to the swipe deck)
-- ============================================================================

insert into public.profiles (
    id, name, age, bio, city, distance_km, relationship_intent,
    occupation, industry, education, education_level, height, gender, interested_in,
    languages, is_verified, is_online, character_badge, character_description,
    show_character_badge, photo_urls, interests, hobbies, looking_for,
    lifestyle, prompts, compatibility_score, profile_completion_score,
    onboarding_completed, onboarding_step, created_at, updated_at
)
values
    -- 1 · Sarah — UX designer, Mumbai
    ('5eed0001-0000-4000-8000-000000000001', 'Sarah Mathews', 26,
     'UX designer who sketches strangers on the metro. Strong opinions about coffee, weak resistance to beach sunsets. Ask me about the time I got lost in Lisbon — best day ever.',
     'Mumbai', 8, 'Long-term relationship', 'UX Designer', 'Design', 'B.Des, NID Ahmedabad', 'Bachelor''s Degree', '164 cm', 'Female', 'Men',
     array['English','Malayalam','Hindi'], true, true, 'The Explorer', 'Driven by curiosity, finding unique experiences and spontaneous adventures.',
     true, array['https://randomuser.me/api/portraits/women/44.jpg','https://randomuser.me/api/portraits/women/68.jpg','https://randomuser.me/api/portraits/women/21.jpg'],
     array['Music','Movies','Travel','Coffee','Art'], array['Painting','Reading'], array['Dating','Relationship'],
     '{"smoking":"No","drinking":"Socially","exercise":"Sometimes","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"My simple pleasures","answer":"Filter coffee + a rainy window + a good playlist"}]'::jsonb,
     94, 96, true, 5, now() - interval '30 days', now()),

    -- 2 · Priya — dancer, Kochi
    ('5eed0001-0000-4000-8000-000000000002', 'Priya Nair', 24,
     'Bharatanatyam dancer by evening, electrical engineer by day. I will absolutely drag you to a rooftop to watch the sunset. Warning: terrible at board games, unbeatable at karaoke.',
     'Kochi', 21, 'Figuring it out', 'Dance Instructor', 'Performing Arts', 'B.Tech', 'Bachelor''s Degree', '158 cm', 'Female', 'Everyone',
     array['Malayalam','English','Tamil'], true, false, 'The Playful One', 'Brings humor, vibrant energy, and high game engagement to every interaction.',
     true, array['https://randomuser.me/api/portraits/women/31.jpg','https://randomuser.me/api/portraits/women/57.jpg'],
     array['Dancing','Music','Movies','Theatre'], array['Dancing'], array['Dating','Socializing'],
     '{"smoking":"No","drinking":"Never","exercise":"Often","diet":"Vegetarian"}'::jsonb,
     '[{"question":"The way to win me over","answer":"Learn one Mohiniyattam move and perform it badly"}]'::jsonb,
     88, 92, true, 5, now() - interval '30 days', now()),

    -- 3 · Ananya — engineer, Bengaluru
    ('5eed0001-0000-4000-8000-000000000003', 'Ananya Rao', 27,
     'Backend engineer, weekend trekker, cat co-parent. I run on filter coffee and pull requests. Looking for someone to share summit photos and 2am street food runs with.',
     'Bengaluru', 34, 'Long-term relationship', 'Software Engineer', 'Technology', 'B.E. CS, RVCE', 'Bachelor''s Degree', '167 cm', 'Female', 'Men',
     array['English','Kannada','Hindi'], true, true, 'The Deep Thinker', 'Prefers meaningful philosophical chats over superficial banter.',
     true, array['https://randomuser.me/api/portraits/women/63.jpg','https://randomuser.me/api/portraits/women/81.jpg','https://randomuser.me/api/portraits/women/16.jpg'],
     array['Technology','Startups','Board Games','Hiking','Coffee'], array['Hiking'], array['Relationship','Networking'],
     '{"smoking":"No","drinking":"Socially","exercise":"Often","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"I geek out on","answer":"Distributed systems trivia and names of mountain passes"}]'::jsonb,
     92, 95, true, 5, now() - interval '30 days', now()),

    -- 4 · Meera — chef, Chennai
    ('5eed0001-0000-4000-8000-000000000004', 'Meera Pillai', 25,
     'Chef-in-training with a Chettinad grandmother''s recipes and a Michelin dream. I cook when I''m happy, and I cook when I''m stressed — either way, you eat well.',
     'Chennai', 12, 'Long-term relationship', 'Sous Chef', 'Hospitality', 'Culinary Diploma', 'Professional Qualification', '161 cm', 'Female', 'Men',
     array['Tamil','English'], true, false, 'The Nurturer', 'Cares loudly — feeds you, checks you got home, remembers your exam dates.',
     true, array['https://randomuser.me/api/portraits/women/47.jpg','https://randomuser.me/api/portraits/women/25.jpg'],
     array['Cooking','Foodie','Yoga','Wine & Dine','Travel'], array['Cooking'], array['Relationship','Long-term Relationship'],
     '{"smoking":"No","drinking":"Socially","exercise":"Sometimes","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"Cook with me","answer":"Sunday mornings are for filter coffee and fresh idli batter"}]'::jsonb,
     90, 93, true, 5, now() - interval '30 days', now()),

    -- 5 · Diya — stylist, Delhi
    ('5eed0001-0000-4000-8000-000000000005', 'Diya Sharma', 23,
     'Fashion stylist, thrifting evangelist, dog aunt to three very good boys. My camera roll is 70% outfits, 30% sunsets. Life''s too short for boring colours.',
     'Delhi', 28, 'Casual dating', 'Fashion Stylist', 'Fashion', 'NIFT Delhi', 'Bachelor''s Degree', '170 cm', 'Female', 'Everyone',
     array['Hindi','English','Punjabi'], true, true, 'The Social Spark', 'Brings people together with warm social energy, dynamic stories, and quick laughs.',
     true, array['https://randomuser.me/api/portraits/women/26.jpg','https://randomuser.me/api/portraits/women/90.jpg'],
     array['Fashion','Art','Photography','Music','Coffee'], array['Painting'], array['Dating','Casual Connection','Socializing'],
     '{"smoking":"No","drinking":"Socially","exercise":"Sometimes","diet":"Eggetarian"}'::jsonb,
     '[{"question":"Unusual skill","answer":"Guessing a thrift store price within 20 rupees"}]'::jsonb,
     86, 90, true, 5, now() - interval '30 days', now()),

    -- 6 · Riya — architect, Jaipur
    ('5eed0001-0000-4000-8000-000000000006', 'Riya Kapoor', 28,
     'Architect restoring havelis. I collect doors — photographs of them, at least. Weekend museum person, weekday podcast-on-commute person. Ask before borrowing my pencil.',
     'Jaipur', 45, 'Marriage minded', 'Conservation Architect', 'Architecture', 'M.Arch', 'Master''s Degree', '165 cm', 'Female', 'Men',
     array['Hindi','English'], true, false, 'The Curious One', 'Loves learning new perspectives and asking questions that uncover unexpected stories.',
     true, array['https://randomuser.me/api/portraits/women/12.jpg','https://randomuser.me/api/portraits/women/79.jpg','https://randomuser.me/api/portraits/women/33.jpg'],
     array['Travel','Photography','Art','Coffee','Podcasts'], array['Photography'], array['Relationship','Long-term Relationship'],
     '{"smoking":"No","drinking":"Never","exercise":"Sometimes","diet":"Vegetarian"}'::jsonb,
     '[{"question":"Best trip I''ve taken","answer":"A 400-year-old haveli in Shekhawati with a rooftop full of pigeons"}]'::jsonb,
     91, 94, true, 5, now() - interval '30 days', now()),

    -- 7 · Tara — doctor, Pune
    ('5eed0001-0000-4000-8000-000000000007', 'Tara Menon', 30,
     'Paediatric resident, marathon-in-progress, plant mom to a stubborn monstera. My shifts are wild, my goss is better. Come for the coffee, stay for the terrible medical puns.',
     'Pune', 18, 'Long-term relationship', 'Paediatric Resident', 'Healthcare', 'MBBS, MD', 'Doctorate', '168 cm', 'Female', 'Men',
     array['English','Marathi','Malayalam'], true, true, 'The Caregiver', 'Shows up with soup, answers at 3am, and somehow always has paracetamol.',
     true, array['https://randomuser.me/api/portraits/women/55.jpg','https://randomuser.me/api/portraits/women/72.jpg'],
     array['Running','Reading','Yoga','Coffee','Pets'], array['Reading'], array['Relationship'],
     '{"smoking":"No","drinking":"Never","exercise":"Often","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"My love language","answer":"Showing up — airport pickups included"}]'::jsonb,
     93, 97, true, 5, now() - interval '30 days', now()),

    -- 8 · Kavya — student, Coimbatore
    ('5eed0001-0000-4000-8000-000000000008', 'Kavya Iyer', 22,
     'Final-year student, anime connoisseur, part-time barista. I make a mean flat white and an even meaner argument about which Ghibli film is best (it''s Spirited Away).',
     'Coimbatore', 6, 'Figuring it out', 'Student', 'Education', 'B.A. Lit (final year)', 'Bachelor''s Degree', '160 cm', 'Female', 'Everyone',
     array['Tamil','English'], false, true, 'The Playful One', 'Brings humor, vibrant energy, and high game engagement to every interaction.',
     true, array['https://randomuser.me/api/portraits/women/19.jpg','https://randomuser.me/api/portraits/women/42.jpg'],
     array['Anime','Music','Coffee','Gaming','Movies'], array['Gaming'], array['Friendship','Dating','Gaming Friends'],
     '{"smoking":"No","drinking":"Never","exercise":"Sometimes","diet":"Vegetarian"}'::jsonb,
     '[{"question":"Weekend plan","answer":"One Piece marathon and too much ramen"}]'::jsonb,
     84, 88, true, 5, now() - interval '30 days', now()),

    -- 9 · Nithya — photographer, Udupi
    ('5eed0001-0000-4000-8000-000000000009', 'Nithya Bhat', 29,
     'Wedding photographer — I cry at other people''s vows for a living. Coastline kid, sunset chaser, spreadsheet nerd. My dog Coconut approves this profile.',
     'Udupi', 37, 'New friends & connections', 'Photographer', 'Creative', 'BFA', 'Bachelor''s Degree', '163 cm', 'Female', 'Everyone',
     array['Kannada','Tulu','English'], true, false, 'The Adventurer', 'Always ready to try something new, from outdoor quests to eccentric date spots.',
     true, array['https://randomuser.me/api/portraits/women/36.jpg','https://randomuser.me/api/portraits/women/60.jpg'],
     array['Photography','Travel','Pets','Camping','Coffee'], array['Photography'], array['Friendship','Socializing','Dating'],
     '{"smoking":"No","drinking":"Socially","exercise":"Often","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"My studio assistant","answer":"Coconut, 4, golden retriever, accepts payment in belly rubs"}]'::jsonb,
     87, 91, true, 5, now() - interval '30 days', now()),

    -- 10 · Arjun — product manager, Bengaluru
    ('5eed0001-0000-4000-8000-00000000000a', 'Arjun Nair', 27,
     'Product manager by day, Catan strategist by night. I make spreadsheets for fun and filter coffee out of necessity. Trail runs on Saturdays, long drives on Sundays.',
     'Bengaluru', 31, 'Long-term relationship', 'Product Manager', 'Technology', 'B.Tech, NIT Calicut', 'Bachelor''s Degree', '175 cm', 'Male', 'Women',
     array['Malayalam','English','Hindi'], true, true, 'The Conversationalist', 'Engages deeply with thoughtful questions and consistent, genuine communication.',
     true, array['https://randomuser.me/api/portraits/men/32.jpg','https://randomuser.me/api/portraits/men/75.jpg'],
     array['Board Games','Gaming','Technology','Running','Coffee'], array['Gaming','Football'], array['Relationship','Gaming Friends'],
     '{"smoking":"No","drinking":"Socially","exercise":"Often","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"Settle this debate","answer":"Catan is 70% luck and I will defend the other 30%"}]'::jsonb,
     90, 93, true, 5, now() - interval '30 days', now()),

    -- 11 · Rahul — cricket coach, Delhi
    ('5eed0001-0000-4000-8000-00000000000b', 'Rahul Verma', 29,
     'Cricket coach, gym rat, gully-cricket legend of Lajpat Nagar. I talk tactics, make excellent chai, and quote Hera Phera like scripture. Morning person, reluctantly.',
     'Delhi', 26, 'Casual dating', 'Cricket Coach', 'Sports', 'BPEd', 'Professional Qualification', '179 cm', 'Male', 'Women',
     array['Hindi','English'], true, false, 'The Social Spark', 'Brings people together with warm social energy, dynamic stories, and quick laughs.',
     true, array['https://randomuser.me/api/portraits/men/22.jpg','https://randomuser.me/api/portraits/men/56.jpg'],
     array['Sports','Fitness','Running','Movies','Foodie'], array['Playing Cricket','Football'], array['Dating','Socializing'],
     '{"smoking":"No","drinking":"Socially","exercise":"Daily","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"Green flags","answer":"Knows the fielding positions and laughs at my jokes on time"}]'::jsonb,
     85, 89, true, 5, now() - interval '30 days', now()),

    -- 12 · Aditya — music producer, Hyderabad
    ('5eed0001-0000-4000-8000-00000000000c', 'Aditya Rao', 25,
     'Indie music producer with a bedroom studio and big dreams. I sample everything — including your voice if you say something quotable. Vinyl nights > club nights.',
     'Hyderabad', 40, 'Figuring it out', 'Music Producer', 'Music', 'Audio Engineering cert', 'Professional Qualification', '172 cm', 'Male', 'Everyone',
     array['Telugu','English','Hindi'], false, true, 'The Curious One', 'Loves learning new perspectives and asking questions that uncover unexpected stories.',
     true, array['https://randomuser.me/api/portraits/men/41.jpg','https://randomuser.me/api/portraits/men/83.jpg'],
     array['Music','Singing','Podcasts','Gaming','Anime'], array['Playing Musical Instruments'], array['Friendship','Dating','Gaming Friends'],
     '{"smoking":"No","drinking":"Never","exercise":"Sometimes","diet":"Eggetarian"}'::jsonb,
     '[{"question":"Currently on loop","answer":"A 6/8 kuthu beat I definitely overworked"}]'::jsonb,
     83, 87, true, 5, now() - interval '30 days', now()),

    -- 13 · Karthik — chef, Kochi
    ('5eed0001-0000-4000-8000-00000000000d', 'Karthik Menon', 31,
     'Chef, boat-owner-in-training, spice-level extremist. I ferment things in jars and opinions about pineapple on pizza. Feed people for joy — literally, it''s the job.',
     'Kochi', 23, 'Long-term relationship', 'Head Chef', 'Hospitality', 'Hotel Management', 'Bachelor''s Degree', '176 cm', 'Male', 'Women',
     array['Malayalam','English','Tamil'], true, false, 'The Nurturer', 'Cares loudly — feeds you, checks you got home, remembers your exam dates.',
     true, array['https://randomuser.me/api/portraits/men/15.jpg','https://randomuser.me/api/portraits/men/67.jpg'],
     array['Cooking','Foodie','Wine & Dine','Travel','Pets'], array['Cooking'], array['Relationship','Long-term Relationship'],
     '{"smoking":"No","drinking":"Socially","exercise":"Often","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"First date, I''ll take you to","answer":"A toddy shop with great karimeen — trust me"}]'::jsonb,
     89, 92, true, 5, now() - interval '30 days', now()),

    -- 14 · Vikram — cyclist, Mangalore
    ('5eed0001-0000-4000-8000-00000000000e', 'Vikram Shetty', 26,
     'Cycling club founder, sunrise person by force. Coastal rides, filter coffee pit stops, zero indoor personality in monsoon. Will out-pedal you uphill, then buy you breakfast.',
     'Mangalore', 15, 'Long-term relationship', 'Logistics Analyst', 'Logistics', 'B.Com', 'Bachelor''s Degree', '180 cm', 'Male', 'Women',
     array['Kannada','Tulu','English'], true, true, 'The Adventurer', 'Always ready to try something new, from outdoor quests to eccentric date spots.',
     true, array['https://randomuser.me/api/portraits/men/86.jpg','https://randomuser.me/api/portraits/men/51.jpg'],
     array['Cycling','Fitness','Camping','Coffee','Running'], array['Photography'], array['Dating','Relationship'],
     '{"smoking":"No","drinking":"Never","exercise":"Daily","diet":"Non-vegetarian"}'::jsonb,
     '[{"question":"Sunday, 6am","answer":"80km along the coast — you bring the snacks"}]'::jsonb,
     88, 91, true, 5, now() - interval '30 days', now()),

    -- 15 · Dev — founder, Ahmedabad
    ('5eed0001-0000-4000-8000-00000000000f', 'Dev Patel', 28,
     'Second-time founder, first-time dog dad. I read term sheets and cricket scorecards with equal attention. Building a D2C brand, memorising every chai stall in the city.',
     'Ahmedabad', 43, 'Long-term relationship', 'Startup Founder', 'Consumer', 'MBA', 'Master''s Degree', '177 cm', 'Male', 'Women',
     array['Gujarati','Hindi','English'], true, false, 'The Connector', 'Naturally finds common ground and builds fast, genuine rapport.',
     true, array['https://randomuser.me/api/portraits/men/60.jpg','https://randomuser.me/api/portraits/men/29.jpg'],
     array['Startups','Technology','Coffee','Podcasts','Running'], array['Running','Photography'], array['Relationship','Networking'],
     '{"smoking":"No","drinking":"Socially","exercise":"Sometimes","diet":"Vegetarian"}'::jsonb,
     '[{"question":"Founder lesson","answer":"Nobody knows what they''re doing — be kind and ship fast"}]'::jsonb,
     87, 90, true, 5, now() - interval '30 days', now())
on conflict (id) do update set
    name = excluded.name,
    age = excluded.age,
    bio = excluded.bio,
    city = excluded.city,
    distance_km = excluded.distance_km,
    relationship_intent = excluded.relationship_intent,
    occupation = excluded.occupation,
    industry = excluded.industry,
    education = excluded.education,
    education_level = excluded.education_level,
    height = excluded.height,
    gender = excluded.gender,
    interested_in = excluded.interested_in,
    languages = excluded.languages,
    is_verified = excluded.is_verified,
    is_online = excluded.is_online,
    character_badge = excluded.character_badge,
    character_description = excluded.character_description,
    show_character_badge = excluded.show_character_badge,
    photo_urls = excluded.photo_urls,
    interests = excluded.interests,
    hobbies = excluded.hobbies,
    looking_for = excluded.looking_for,
    lifestyle = excluded.lifestyle,
    prompts = excluded.prompts,
    compatibility_score = excluded.compatibility_score,
    profile_completion_score = excluded.profile_completion_score,
    onboarding_completed = true,
    onboarding_step = 5,
    updated_at = now();


-- ============================================================================
-- 3. USER_INTERESTS MIRROR (shared-interest matching joins on this table)
-- ============================================================================

insert into public.user_interests (user_id, interest, source)
select p.id, i, 'SYSTEM'
from public.profiles p
cross join unnest(p.interests) as i
where left(p.id::text, 8) = '5eed0001'
on conflict (user_id, interest) do nothing;


-- ============================================================================
-- 4. "THEY LIKED YOU" — 6 dummies pre-liked every real account, so the
--    first LIKE from the user instantly becomes a mutual MATCH.
-- ============================================================================

insert into public.likes (user_id, target_user_id, action)
select d.id, u.id, 'LIKE'
from auth.users u
cross join (values
    ('5eed0001-0000-4000-8000-000000000001'::uuid),
    ('5eed0001-0000-4000-8000-000000000003'::uuid),
    ('5eed0001-0000-4000-8000-000000000005'::uuid),
    ('5eed0001-0000-4000-8000-00000000000a'::uuid),
    ('5eed0001-0000-4000-8000-00000000000c'::uuid),
    ('5eed0001-0000-4000-8000-00000000000f'::uuid)
) as d(id)
where u.email not like '%@quicky.seed'
on conflict (user_id, target_user_id) do nothing;


-- ============================================================================
-- 5. SEEDED CHAT CONVERSATIONS — for EVERY real account: 5 matches with a
--    message history (sender ids follow the app's convention: the seeded
--    partner sends with their profile uuid; "user_me" marks the user's own
--    outgoing rows). conversation_id = 'match_' || <partner profile id> —
--    exactly what the app uses when replying in the thread.
-- ============================================================================

-- ---- Conversation 1 · Sarah Mathews (26h ago, 1 unread) ----
do $$
declare
    u uuid; a uuid; b uuid;
    d constant uuid := '5eed0001-0000-4000-8000-000000000001';
    conv constant text := 'match_5eed0001-0000-4000-8000-000000000001';
begin
    for u in select id from auth.users where email not like '%@quicky.seed' loop
        a := least(u, d); b := greatest(u, d);
        insert into public.matches (user_a_id, user_b_id, matched_at, last_message, is_new, has_active_game)
        values (a, b, now() - interval '26 hours', 'Perfect. Saturday, 7, the filter-coffee place near the lake ☕', false, false)
        on conflict (user_a_id, user_b_id) do nothing;

        if not exists (select 1 from public.messages where conversation_id = conv) then
            insert into public.messages (conversation_id, sender_id, text, is_read, created_at) values
                (conv, d::text, 'Heyy! Your profile popped up on my feed and I had to say hi 👋', true, now() - interval '26 hours'),
                (conv, 'user_me', 'Hi Sarah! Great to match — those Lisbon sketches are so good!', true, now() - interval '25 hours'),
                (conv, d::text, 'Right?! Best detour I ever took. Okay important question — coffee person or chai person?', true, now() - interval '25 hours'),
                (conv, 'user_me', 'Coffee, obviously. But I respect the chai agenda 😄', true, now() - interval '24 hours'),
                (conv, d::text, 'Good answer, you may live 😌 There''s a tiny filter-coffee place near the lake — Saturday?', true, now() - interval '23 hours'),
                (conv, 'user_me', 'Saturday sounds perfect. 7pm?', true, now() - interval '22 hours'),
                (conv, d::text, 'Perfect. Saturday, 7, the filter-coffee place near the lake ☕', false, now() - interval '20 hours');
        end if;
    end loop;
end $$;

-- ---- Conversation 2 · Ananya Rao (2 days ago, 2 unread) ----
do $$
declare
    u uuid; a uuid; b uuid;
    d constant uuid := '5eed0001-0000-4000-8000-000000000003';
    conv constant text := 'match_5eed0001-0000-4000-8000-000000000003';
begin
    for u in select id from auth.users where email not like '%@quicky.seed' loop
        a := least(u, d); b := greatest(u, d);
        insert into public.matches (user_a_id, user_b_id, matched_at, last_message, is_new, has_active_game)
        values (a, b, now() - interval '2 days', 'Deal — but I''m picking the trail 🥾', false, false)
        on conflict (user_a_id, user_b_id) do nothing;

        if not exists (select 1 from public.messages where conversation_id = conv) then
            insert into public.messages (conversation_id, sender_id, text, is_read, created_at) values
                (conv, d::text, 'Your playlist is dangerous. I''ve had that lo-fi track on repeat all morning 🎧', true, now() - interval '2 days'),
                (conv, 'user_me', 'Haha welcome to my personality — music first, everything else later', true, now() - interval '2 days' + interval '40 minutes'),
                (conv, d::text, 'Respect. Also I saw board games in your interests — Catan ally or Catan enemy?', true, now() - interval '2 days' + interval '2 hours'),
                (conv, 'user_me', 'Depends. Do you rob me in the first three turns?', true, now() - interval '2 days' + interval '5 hours'),
                (conv, d::text, 'I was GOING to say ally, but now the long settlement is looking shaky 👀', false, now() - interval '1 day'),
                (conv, d::text, 'Deal — but I''m picking the trail 🥾', false, now() - interval '5 hours');
        end if;
    end loop;
end $$;

-- ---- Conversation 3 · Diya Sharma (3 days ago, read) ----
do $$
declare
    u uuid; a uuid; b uuid;
    d constant uuid := '5eed0001-0000-4000-8000-000000000005';
    conv constant text := 'match_5eed0001-0000-4000-8000-000000000005';
begin
    for u in select id from auth.users where email not like '%@quicky.seed' loop
        a := least(u, d); b := greatest(u, d);
        insert into public.matches (user_a_id, user_b_id, matched_at, last_message, is_new, has_active_game)
        values (a, b, now() - interval '3 days', 'Sent you the Pinterest board — prepare to be amazed ✨', false, false)
        on conflict (user_a_id, user_b_id) do nothing;

        if not exists (select 1 from public.messages where conversation_id = conv) then
            insert into public.messages (conversation_id, sender_id, text, is_read, created_at) values
                (conv, d::text, 'Okay verdict needed: cowboy boots in 2026 — yes or absolutely not 👢', true, now() - interval '3 days'),
                (conv, 'user_me', 'Bold of you to ask someone whose entire wardrobe is three shades of grey', true, now() - interval '3 days' + interval '1 hour'),
                (conv, d::text, 'That''s exactly why I''m asking — you''re the control group 😌', true, now() - interval '3 days' + interval '2 hours'),
                (conv, 'user_me', 'Fine: yes, but ONLY with the right denim', true, now() - interval '3 days' + interval '4 hours'),
                (conv, d::text, 'Sent you the Pinterest board — prepare to be amazed ✨', true, now() - interval '2 days');
        end if;
    end loop;
end $$;

-- ---- Conversation 4 · Arjun Nair (5 hours ago, 1 unread, active game) ----
do $$
declare
    u uuid; a uuid; b uuid;
    d constant uuid := '5eed0001-0000-4000-8000-00000000000a';
    conv constant text := 'match_5eed0001-0000-4000-8000-00000000000a';
begin
    for u in select id from auth.users where email not like '%@quicky.seed' loop
        a := least(u, d); b := greatest(u, d);
        insert into public.matches (user_a_id, user_b_id, matched_at, last_message, is_new, has_active_game)
        values (a, b, now() - interval '5 hours', 'Truth: what''s the most irrational hill you''d die on? 🎲', false, true)
        on conflict (user_a_id, user_b_id) do nothing;

        if not exists (select 1 from public.messages where conversation_id = conv) then
            insert into public.messages (conversation_id, sender_id, text, is_read, created_at) values
                (conv, d::text, 'Quick one before the sprint meeting — team Catan or team Monopoly? Careful, this decides everything', true, now() - interval '5 hours'),
                (conv, 'user_me', 'Catan, and I don''t even feel bad about the sheep empire I''m building', true, now() - interval '4 hours'),
                (conv, d::text, 'A person of culture 😌 Round two tonight? Loser picks the coffee', true, now() - interval '3 hours'),
                (conv, 'user_me', 'Deal. Truth or Dare first?', true, now() - interval '2 hours'),
                (conv, d::text, 'Truth: what''s the most irrational hill you''d die on? 🎲', false, now() - interval '1 hour');
        end if;
    end loop;
end $$;

-- ---- Conversation 5 · Aditya Rao (1 day ago, read) ----
do $$
declare
    u uuid; a uuid; b uuid;
    d constant uuid := '5eed0001-0000-4000-8000-00000000000c';
    conv constant text := 'match_5eed0001-0000-4000-8000-00000000000c';
begin
    for u in select id from auth.users where email not like '%@quicky.seed' loop
        a := least(u, d); b := greatest(u, d);
        insert into public.matches (user_a_id, user_b_id, matched_at, last_message, is_new, has_active_game)
        values (a, b, now() - interval '1 day', 'Sending the demo tonight — judge gently 🎧', false, false)
        on conflict (user_a_id, user_b_id) do nothing;

        if not exists (select 1 from public.messages where conversation_id = conv) then
            insert into public.messages (conversation_id, sender_id, text, is_read, created_at) values
                (conv, d::text, 'Yo — is that a vinyl collection in your third photo? Instant respect', true, now() - interval '1 day'),
                (conv, 'user_me', 'Only the essentials. And like four impulse buys I regret nothing about', true, now() - interval '1 day' + interval '50 minutes'),
                (conv, d::text, 'The best collections are 40% regret. I''m producing a lo-fi EP — need a test listener', true, now() - interval '1 day' + interval '3 hours'),
                (conv, 'user_me', 'I''m aggressively available for that', true, now() - interval '1 day' + interval '4 hours'),
                (conv, d::text, 'Sending the demo tonight — judge gently 🎧', true, now() - interval '10 hours');
        end if;
    end loop;
end $$;


-- ============================================================================
-- 6. MARK-AS-READ SUPPORT (needed by the seeded chats)
--    The app now loads conversation history on sign-in and counts unread
--    incoming messages; opening a chat clears the badge server-side.
--    Scope matches the existing messages policies (authenticated users).
-- ============================================================================

drop policy if exists "messages_update_mark_read" on public.messages;
create policy "messages_update_mark_read" on public.messages
    for update using (auth.role() = 'authenticated')
    with check (auth.role() = 'authenticated');


-- ============================================================================
-- DONE — after running, sign out & back into the app (or restart it):
--   · Discover shows 15 seed profiles to swipe (6 of them pre-liked you).
--   · Chats shows 5 conversations with history + unread badges.
-- ============================================================================
