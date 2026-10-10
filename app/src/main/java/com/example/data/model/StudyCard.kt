package com.example.data.model

data class StudyCard(
    val id: String,
    val subject: String,
    val title: String,
    val summary: String,
    val detailedNotes: String,
    val keyTerms: List<String>,
    val difficulty: String = "Medium"
)

data class QuizQuestion(
    val id: String,
    val subject: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class StudyTopic(
    val id: String,
    val name: String,
    val iconName: String,
    val count: Int
)

object SampleStudyData {
    val topics = listOf(
        StudyTopic("all", "All Topics", "School", 24),
        StudyTopic("med", "Cardiology", "Favorite", 8),
        StudyTopic("bio", "Cell Biology", "Biotech", 6),
        StudyTopic("pharm", "Pharmacology", "Medication", 5),
        StudyTopic("anat", "Neuroanatomy", "Psychology", 5)
    )

    val cards = listOf(
        StudyCard(
            id = "c1",
            subject = "Cardiology",
            title = "Cardiac Action Potential (Phase 0-4)",
            summary = "Depolarization initiated by rapid Na+ influx, plateau maintained by L-type Ca2+ channels, repolarization by delayed rectifier K+.",
            detailedNotes = "Phase 0: Rapid depolarization via voltage-gated Na+ channels.\nPhase 1: Early rapid repolarization with transient outward K+ current.\nPhase 2: Plateau phase balanced by inward Ca2+ (L-type) and outward K+ currents.\nPhase 3: Final repolarization driven by I_Kr and I_Ks currents.\nPhase 4: Resting membrane potential maintained by inward rectifier K+ channels (I_K1).",
            keyTerms = listOf("L-type Ca2+", "Phase 2 Plateau", "Delayed Rectifier K+", "Refractory Period"),
            difficulty = "Hard"
        ),
        StudyCard(
            id = "c2",
            subject = "Cell Biology",
            title = "Mitochondrial Electron Transport Chain",
            summary = "Complex I-IV protein pumps generate electrochemical proton gradient across inner membrane driving ATP synthase.",
            detailedNotes = "NADH enters at Complex I (NADH dehydrogenase), while FADH2 enters at Complex II (Succinate dehydrogenase). Coenzyme Q transfers electrons to Complex III (Cytochrome bc1), then Cytochrome c delivers to Complex IV (Cytochrome c oxidase) where O2 is reduced to H2O. Complex V (ATP Synthase) harnesses proton motive force.",
            keyTerms = listOf("Complex I-V", "Proton Gradient", "Ubiquinone", "Chemiosmosis"),
            difficulty = "Medium"
        ),
        StudyCard(
            id = "c3",
            subject = "Pharmacology",
            title = "Beta-Adrenergic Receptor Antagonists",
            summary = "Cardioselective beta-1 blockers reduce chronotropic and inotropic cardiac workload, lowering myocardial oxygen demand.",
            detailedNotes = "Beta-1 selective blockers (e.g. Metoprolol, Atenolol, Bisoprolol) primarily antagonize cardiac beta-1 receptors. Non-selective agents (e.g. Propranolol) also block beta-2 receptors, risking bronchoconstriction in asthmatic patients. Carvedilol and Labetalol add alpha-1 adrenergic blockade for peripheral vasodilation.",
            keyTerms = listOf("Metoprolol", "Cardioselectivity", "Beta-1 Adrenoreceptors", "Negative Inotrope"),
            difficulty = "Medium"
        ),
        StudyCard(
            id = "c4",
            subject = "Neuroanatomy",
            title = "Circle of Willis & Cerebral Perfusion",
            summary = "Anastomotic arterial polygon at skull base interconnecting anterior (internal carotid) and posterior (vertebrobasilar) circulations.",
            detailedNotes = "Components: Anterior cerebral arteries (ACA), Anterior communicating artery (AComA), Internal carotid arteries (ICA), Posterior communicating arteries (PComA), and Posterior cerebral arteries (PCA). Provides collateral circulation preventing ischemia when one feeding vessel experiences hypoperfusion.",
            keyTerms = listOf("AComA", "PComA", "Basilar Artery", "Collateral Perfusion"),
            difficulty = "Hard"
        ),
        StudyCard(
            id = "c5",
            subject = "Cell Biology",
            title = "CRISPR-Cas9 Endonuclease Mechanism",
            summary = "Bacterial adaptive immunity repurposed for targeted double-strand DNA cleavage guided by single-guide RNA (sgRNA).",
            detailedNotes = "The sgRNA pairs with target genomic sequence adjacent to a Protospacer Adjacent Motif (PAM: 5'-NGG-3'). Cas9 induces a double-strand break (DSB) ~3 base pairs upstream of PAM. Cellular repair pathways include Non-Homologous End Joining (NHEJ, gene knockout) or Homology-Directed Repair (HDR, gene knockin).",
            keyTerms = listOf("PAM Sequence (5'-NGG)", "sgRNA", "Double Strand Break", "NHEJ vs HDR"),
            difficulty = "Hard"
        ),
        StudyCard(
            id = "c6",
            subject = "Cardiology",
            title = "Frank-Starling Law of the Heart",
            summary = "Stroke volume increases in response to an increase in end-diastolic volume (preload) through optimal actin-myosin overlap.",
            detailedNotes = "Increased venous return leads to increased end-diastolic volume (EDV), causing greater stretch of ventricular sarcomeres towards optimal length (2.2 micrometers). This increases calcium sensitivity of myofilaments, generating stronger systolic contraction force and higher stroke volume.",
            keyTerms = listOf("Preload", "End-Diastolic Volume", "Sarcomere Stretch", "Contractility"),
            difficulty = "Easy"
        )
    )

    val sampleQuizzes = listOf(
        QuizQuestion(
            id = "q1",
            subject = "Cardiology",
            question = "Which ion channel is primarily responsible for the prolonged Phase 2 plateau of ventricular myocytes?",
            options = listOf(
                "Fast voltage-gated Na+ channels",
                "L-type voltage-gated Ca2+ channels",
                "Transient outward K+ channels",
                "Hyperpolarization-activated cyclic nucleotide channels"
            ),
            correctIndex = 1,
            explanation = "L-type Ca2+ channels allow inward Ca2+ influx which balances outward K+ repolarization currents, sustaining the Phase 2 plateau."
        ),
        QuizQuestion(
            id = "q2",
            subject = "Pharmacology",
            question = "Why are non-selective beta-blockers like Propranolol contraindicated in patients with severe asthma?",
            options = listOf(
                "They induce excess histamine release",
                "They block beta-2 receptors causing bronchoconstriction",
                "They directly activate muscarinic M3 receptors",
                "They cause profound respiratory alkalosis"
            ),
            correctIndex = 1,
            explanation = "Non-selective beta blockers block beta-2 receptors in bronchial smooth muscle, preventing bronchodilation and risking life-threatening bronchospasm."
        ),
        QuizQuestion(
            id = "q3",
            subject = "Cell Biology",
            question = "At which complex of the mitochondrial electron transport chain does FADH2 deposit its high-energy electrons?",
            options = listOf(
                "Complex I (NADH Dehydrogenase)",
                "Complex II (Succinate Dehydrogenase)",
                "Complex III (Cytochrome bc1)",
                "Complex IV (Cytochrome c Oxidase)"
            ),
            correctIndex = 1,
            explanation = "FADH2 transfers electrons directly to Complex II (Succinate Dehydrogenase), which then reduces Coenzyme Q without directly pumping protons."
        )
    )
}
