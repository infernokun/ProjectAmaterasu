--
-- PostgreSQL database dump
--

-- Dumped from database version 16.3 (Debian 16.3-1.pgdg120+1)
-- Dumped by pg_dump version 16.3 (Debian 16.3-1.pgdg120+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: lab; Type: TABLE; Schema: public; Owner: infernokun
--

CREATE TABLE public.lab (
    id character varying(255) NOT NULL,
    created_date timestamp(6) without time zone,
    last_modified_date timestamp(6) without time zone,
    capacity integer,
    created_by character varying(255),
    description character varying(255),
    docker_file character varying(255),
    name character varying(255),
    status smallint,
    version character varying(255),
    CONSTRAINT lab_status_check CHECK (((status >= 0) AND (status <= 2)))
);


ALTER TABLE public.lab OWNER TO infernokun;

--
-- Name: lab_tracker; Type: TABLE; Schema: public; Owner: infernokun
--

CREATE TABLE public.lab_tracker (
    id character varying(255) NOT NULL,
    created_date timestamp(6) without time zone,
    last_modified_date timestamp(6) without time zone,
    lab_status smallint,
    team_id character varying(255),
    created_by character varying(255),
    lab_id character varying(255),
    CONSTRAINT lab_tracker_lab_status_check CHECK (((lab_status >= 0) AND (lab_status <= 4)))
);


ALTER TABLE public.lab_tracker OWNER TO infernokun;

--
-- Name: team; Type: TABLE; Schema: public; Owner: infernokun
--

CREATE TABLE public.team (
    id character varying(255) NOT NULL,
    created_date timestamp(6) without time zone,
    last_modified_date timestamp(6) without time zone,
    description character varying(255),
    team_name character varying(255),
    name character varying(255),
    created_by character varying(255)
);


ALTER TABLE public.team OWNER TO infernokun;

--
-- Name: users; Type: TABLE; Schema: public; Owner: infernokun
--

CREATE TABLE public.users (
    id character varying(255) NOT NULL,
    created_date timestamp(6) without time zone,
    last_modified_date timestamp(6) without time zone,
    username character varying(255),
    team_id character varying(255),
    created_by character varying(255)
);


ALTER TABLE public.users OWNER TO infernokun;

--
-- Data for Name: lab; Type: TABLE DATA; Schema: public; Owner: infernokun
--

COPY public.lab (id, created_date, last_modified_date, capacity, created_by, description, docker_file, name, status, version) FROM stdin;
596fafd3-8732-4ee9-835e-e7c43cf2c2e6	2025-01-25 15:41:12.983542	2025-01-25 15:41:12.983542	10	InfernoKun	A beginner-friendly lab to explore defense operations.	dco-hunt_2025-01-25_15-41-12.yml	dco hunt	0	1.0.0
0cd64fea-4621-4c48-bd8b-a776381220c1	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	10	InfernoKun	An offensive-focused lab for advanced users.	oco-hunt_2025-01-25_15-41-12.yml	oco hunt	0	1.0.0
eb810f99-acee-4cb7-a8d7-4a9ba117d9b5	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	8	CyberMaster	Learn about evasion and detection in cybersecurity.	stealth-ops_2025-01-25_15-41-12.yml	stealth ops	2	1.2.0
55ba914c-f81e-4d51-9d54-aefc4c879651	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	12	SecOpsPro	Hands-on digital forensics training for incident response.	forensics-lab_2025-01-25_15-41-12.yml	forensics lab	0	1.3.5
4f862b8e-d1df-4332-ac72-cffe3381ca39	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	15	InfernoKun	Focus on securing cloud-native applications.	cloud-security_2025-01-25_15-41-12.yml	cloud security	1	2.0.0
28d1118b-8b3a-4cd7-9574-98cf251e6770	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	5	MalwareHunter	Reverse engineering and dynamic malware analysis lab.	malware-analysis_2025-01-25_15-41-12.yml	malware analysis	0	3.1.4
595d52f9-9746-49ed-8a1f-73889e0aa591	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	20	WebDevGuru	Explore common web vulnerabilities and mitigations.	web-security-101_2025-01-25_15-41-12.yml	web security 101	0	1.0.0
5c700721-a934-4948-abb8-167b1d5a4603	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	6	InfernoKun	Learn to secure APIs against attacks like injection and DDoS.	api-pentesting_2025-01-25_15-41-12.yml	api pentesting	2	2.2.1
c0289864-5575-4973-aab1-bef7198eff39	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	8	IoTExpert	Focus on securing Internet of Things devices.	iot-security_2025-01-25_15-41-12.yml	iot security	1	1.5.0
1626c928-21c7-4b79-ba28-aa70f1bbf276	2025-01-25 15:41:12.984766	2025-01-25 15:41:12.984766	12	CryptoNerd	Understand encryption, hashing, and digital signatures.	cryptography-basics_2025-01-25_15-41-12.yml	cryptography basics	0	1.0.2
8d2dd4e9-f3c9-40fa-9c7b-3afb7db03e69	2025-01-25 15:41:12.985381	2025-01-25 15:41:12.985381	7	InfernoKun	Learn to secure Android and iOS applications.	mobile-app-security_2025-01-25_15-41-12.yml	mobile app security	0	3.0.0
5d0fa959-5920-4f09-9036-5a26a60fa945	2025-01-25 15:41:12.985381	2025-01-25 15:41:12.985381	10	CyberTrainer	Experience phishing techniques and how to counteract them.	phishing-simulation_2025-01-25_15-41-12.yml	phishing simulation	2	2.1.0
02d0e1b7-32b4-49e1-a026-1ba544e6882b	2025-01-25 15:41:12.985381	2025-01-25 15:41:12.985381	4	MalwareHunter	Analyze ransomware behavior and create defenses.	ransomware-analysis_2025-01-25_15-41-12.yml	ransomware analysis	1	1.1.3
f905bc8c-6f30-4b95-bead-15630e681c33	2025-01-25 15:41:12.985381	2025-01-25 15:41:12.985381	18	SocEngExpert	Learn how attackers exploit human psychology.	social-engineering_2025-01-25_15-41-12.yml	social engineering	0	2.0.0
c6fb30be-fc5f-4aff-904b-529f7d544487	2025-01-25 15:41:12.985381	2025-01-25 15:41:12.985381	10	ZeroTrustGuru	Implement zero trust security principles in a network.	zero-trust-architecture_2025-01-25_15-41-12.yml	zero trust architecture	0	1.0.1
\.


--
-- Data for Name: lab_tracker; Type: TABLE DATA; Schema: public; Owner: infernokun
--

COPY public.lab_tracker (id, created_date, last_modified_date, lab_status, team_id, created_by, lab_id) FROM stdin;
cc8c6be2-ab60-40d5-9fe1-73fb1b3ba67c	2025-01-25 23:53:36.164134	2025-01-25 23:53:36.164134	0	8fa52d4c-dd4c-45e1-98d4-d8cb985cbe62	6416281e-9a1a-4e76-a4a8-50bb3c15d3c2	596fafd3-8732-4ee9-835e-e7c43cf2c2e6
c1576459-700b-4860-8022-85f668d547a2	2025-01-26 18:32:53.532966	2025-01-26 18:32:53.532966	0	8fa52d4c-dd4c-45e1-98d4-d8cb985cbe62	6416281e-9a1a-4e76-a4a8-50bb3c15d3c2	0cd64fea-4621-4c48-bd8b-a776381220c1
90a4a9dc-791f-4168-9df1-e0e91ca8571a	2025-01-26 18:34:43.955272	2025-01-26 18:34:43.955272	0	8fa52d4c-dd4c-45e1-98d4-d8cb985cbe62	6416281e-9a1a-4e76-a4a8-50bb3c15d3c2	eb810f99-acee-4cb7-a8d7-4a9ba117d9b5
\.


--
-- Data for Name: team; Type: TABLE DATA; Schema: public; Owner: infernokun
--

COPY public.team (id, created_date, last_modified_date, description, team_name, name, created_by) FROM stdin;
8fa52d4c-dd4c-45e1-98d4-d8cb985cbe62	2025-01-25 17:45:10.675092	2025-01-25 17:45:10.675791	best cpt on the planet	\N	CPT 185	\N
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: infernokun
--

COPY public.users (id, created_date, last_modified_date, username, team_id, created_by) FROM stdin;
6416281e-9a1a-4e76-a4a8-50bb3c15d3c2	2025-01-25 17:46:12.271122	2025-01-25 17:46:12.271122	InfernoKun	8fa52d4c-dd4c-45e1-98d4-d8cb985cbe62	\N
\.


--
-- Name: lab lab_pkey; Type: CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.lab
    ADD CONSTRAINT lab_pkey PRIMARY KEY (id);


--
-- Name: lab_tracker lab_tracker_pkey; Type: CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.lab_tracker
    ADD CONSTRAINT lab_tracker_pkey PRIMARY KEY (id);


--
-- Name: team team_pkey; Type: CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.team
    ADD CONSTRAINT team_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: lab_tracker fk9b7jowwjqcnedb8dfc71i7i3b; Type: FK CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.lab_tracker
    ADD CONSTRAINT fk9b7jowwjqcnedb8dfc71i7i3b FOREIGN KEY (team_id) REFERENCES public.team(id);


--
-- Name: lab_tracker fkb5n3glf06nmmfwncdxv7s6od9; Type: FK CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.lab_tracker
    ADD CONSTRAINT fkb5n3glf06nmmfwncdxv7s6od9 FOREIGN KEY (lab_id) REFERENCES public.lab(id);


--
-- Name: users fkhn2tnbh9fqjqeuv8ehw5ple7a; Type: FK CONSTRAINT; Schema: public; Owner: infernokun
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fkhn2tnbh9fqjqeuv8ehw5ple7a FOREIGN KEY (team_id) REFERENCES public.team(id);


--
-- PostgreSQL database dump complete
--

