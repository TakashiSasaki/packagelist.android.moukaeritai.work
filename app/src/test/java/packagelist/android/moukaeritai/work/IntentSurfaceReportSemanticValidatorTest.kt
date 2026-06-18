package packagelist.android.moukaeritai.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class IntentSurfaceReportSemanticValidatorTest {

    private fun createMinimalReport(catalog: IntentInvocationCatalog? = null): IntentSurfaceReport {
        return IntentSurfaceReport(
            schema = 5,
            schema_version = 5,
            schema_id = "urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233",
            run_id = "run_123",
            file_name = "report.json",
            generated_at_epoch_millis = 12345L,
            generated_at_utc = "Z",
            app = AppInfo(""," ",0,0,0,"",null,null,null,null,false,null, emptyList(),emptyList(),false),
            device = DeviceInfo(0,"","","","","","",emptyList(),"","",0,0,0),
            package_visibility = PackageVisibilityInfo("",false,false,emptyList(),""),
            intent_invocation_catalog = catalog,
            probe_families = emptyList(),
            intent_surface_probes = emptyList(),
            component_surface_summary = emptyList(),
            summary = SurfaceDiagnosticSummary(5,"run",0,0,0,0,0,emptyMap(),emptyMap(),emptyMap(),emptyMap(),0,0,0,0,0,0,0,0,0,null,emptyMap()),
            errors = emptyList(),
            events = emptyList()
        )
    }

    private fun createCandidate(
        id: String,
        setApi: String,
        uri: String? = null,
        mimeType: String? = null,
        flags: List<String> = emptyList(),
        grantFlags: List<String> = emptyList(),
        requirements: List<IntentRuntimeRequirement> = emptyList(),
        startActivityAttempted: Boolean = false,
        launchResult: String = CatalogConstants.LAUNCH_NOT_TESTED,
        autoLaunchAllowed: Boolean = false
    ) = IntentInvocationCandidate(
        candidate_id = id,
        source_probe_id = "test_probe",
        source_family = "test_family",
        display_label = "Test Label",
        target = IntentInvocationTarget("com.example.viewer", "com.example.viewer.MainActivity", "com.example.viewer/com.example.viewer.MainActivity"),
        intent_recipe = IntentInvocationRecipe(
            targeting_mode = "COMPONENT_EXPLICIT",
            construction_api = "setComponent",
            action = "android.intent.action.VIEW",
            data = IntentInvocationData(
                set_api = setApi,
                uri = uri,
                uri_kind = "URL",
                scheme = "https",
                display_redacted = uri,
                mime_type = mimeType
            ),
            categories = emptyList(),
            extras = emptyList(),
            clip_data = null,
            flags = flags,
            grant_flags = grantFlags,
            runtime_requirements = requirements
        ),
        evidence = IntentInvocationEvidence(
            implicit_resolution_observed = true,
            implicit_evidence_status = "IMPLICIT_CANDIDATE_OBSERVED",
            implicit_probe_candidate_index = 0,
            package_targeted_resolution_status = null,
            component_static_assessment = "EXPLICIT_COMPONENT_STATIC_OK",
            start_activity_attempted = startActivityAttempted,
            launch_result = launchResult
        ),
        safety = IntentInvocationSafety(
            auto_launch_allowed = autoLaunchAllowed,
            requires_user_confirmation = true,
            side_effect_level = "MAY_OPEN_EXTERNAL_APP",
            notes = emptyList()
        )
    )

    // 8. Semantic validator accepts a minimal valid schema 5 report with catalog.
    @Test
    fun testValidSchema() {
        val validator = IntentSurfaceReportSemanticValidator()
        val catalog = IntentInvocationCatalog(
            candidate_count = 0,
            candidates = emptyList()
        )
        val report = createMinimalReport(catalog)
        val result = validator.validate(report)
        assertTrue(result.errors.joinToString(","), result.isValid)
    }

    @Test
    fun testMissingCatalog() {
        val validator = IntentSurfaceReportSemanticValidator()
        val report = createMinimalReport(catalog = null)
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("intent_invocation_catalog is missing") })
    }

    // 9. Semantic validator rejects fake executable URI placeholders.
    @Test
    fun testValidatorRejectsFakePlaceholder() {
        val validator = IntentSurfaceReportSemanticValidator()
        val mockCand = createCandidate("cand.fake", "setData", uri = "content://example")
        val catalog = IntentInvocationCatalog(candidate_count = 1, candidates = listOf(mockCand))
        val report = createMinimalReport(catalog)
        
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("cannot use fake/redacted URI placeholder") })
    }

    // 10. Semantic validator rejects invalid "data.set_api" combinations.
    @Test
    fun testValidatorRejectsInvalidSetApiCombinations() {
        val validator = IntentSurfaceReportSemanticValidator()
        
        // setData but URI is null
        val cand1 = createCandidate("cand.setDataErr", "setData", uri = null)
        val report1 = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand1)))
        val res1 = validator.validate(report1)
        assertFalse(res1.isValid)
        assertTrue(res1.errors.any { it.contains("setData implies URI is non-null") })

        // none but URI is present
        val cand2 = createCandidate("cand.noneErr", "none", uri = "https://example.com/")
        val report2 = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand2)))
        val res2 = validator.validate(report2)
        assertFalse(res2.isValid)
        assertTrue(res2.errors.any { it.contains("none implies both URI and MIME are null") })
    }

    // 11. Semantic validator rejects missing runtime requirement for runtime-provided data.
    @Test
    fun testValidatorRejectsMissingRuntimeRequirementsForRuntimeProvidedData() {
        val validator = IntentSurfaceReportSemanticValidator()
        
        val cand = createCandidate("cand.runtimeErr", "runtimeProvidedData", uri = null, requirements = emptyList())
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("runtimeProvidedData requires matching runtime_requirements") })
    }

    // 12. Semantic validator rejects unknown flags.
    @Test
    fun testValidatorRejectsUnknownFlags() {
        val validator = IntentSurfaceReportSemanticValidator()
        
        val cand = createCandidate("cand.flagErr", "none", flags = listOf("FLAG_ACTIVITY_NEW_TASK", "SOME_INVALID_FLAG"))
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("Invalid recipe flag") })
    }

    // 13. Semantic validator rejects "auto_launch_allowed = true".
    @Test
    fun testValidatorRejectsAutoLaunchAllowedTrue() {
        val validator = IntentSurfaceReportSemanticValidator()
        
        val cand = createCandidate("cand.autoLaunchErr", "none", autoLaunchAllowed = true)
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("auto_launch_allowed must be false") })
    }

    // 14. Semantic validator rejects "start_activity_attempted = true".
    @Test
    fun testValidatorRejectsStartActivityAttemptedTrue() {
        val validator = IntentSurfaceReportSemanticValidator()
        
        val cand = createCandidate("cand.startAttemptErr", "none", startActivityAttempted = true)
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("start_activity_attempted must be false") })
    }

    // 15. Semantic validator error messages include actual candidate IDs.
    @Test
    fun testValidatorErrorMessagesIncludeActualCandidateIds() {
        val validator = IntentSurfaceReportSemanticValidator()
        val candId = "cand.mySpecialErrorId_123"
        val cand = createCandidate(candId, "setData", uri = null)
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        
        assertFalse(res.isValid)
        // Ensure error message interpolates the exact candidate ID
        assertTrue(res.errors.any { it.contains(candId) })
        // Ensure no literal "${candidate.candidate_id}" is emitted
        assertFalse(res.errors.any { it.contains("\${candidate.candidate_id}") })
    }

    @Test
    fun testValidatorRejectsOldSchemaId() {
        val validator = IntentSurfaceReportSemanticValidator()
        val catalog = IntentInvocationCatalog(candidate_count = 0, candidates = emptyList())
        val report = createMinimalReport(catalog).copy(schema_id = "work.moukaeritai.intent-surface-report.schema.v5")
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("schema_id must be urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233") })
    }

    @Test
    fun testValidatorRejectsCatalogSchemaVersionNotOne() {
        val validator = IntentSurfaceReportSemanticValidator()
        val catalog = IntentInvocationCatalog(catalog_schema_version = 2, candidate_count = 0, candidates = emptyList())
        val report = createMinimalReport(catalog)
        val result = validator.validate(report)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("catalog_schema_version must be 1") })
    }

    @Test
    fun testValidatorRejectsInvalidGrantFlag() {
        val validator = IntentSurfaceReportSemanticValidator()
        val cand = createCandidate("cand.grantErr", "none", grantFlags = listOf("FLAG_GRANT_READ_URI_PERMISSION", "INVALID_GRANT_FLAG"))
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("Invalid grant flag") })
    }

    @Test
    fun testValidatorRejectsInvalidSideEffectLevel() {
        val validator = IntentSurfaceReportSemanticValidator()
        val cand = createCandidate("cand.sideEffectErr", "none").copy(
            safety = IntentInvocationSafety(
                auto_launch_allowed = false,
                requires_user_confirmation = true,
                side_effect_level = "VERY_DANGEROUS_SIDE_EFFECT_LEVEL",
                notes = emptyList()
            )
        )
        val report = createMinimalReport(IntentInvocationCatalog(candidate_count = 1, candidates = listOf(cand)))
        val res = validator.validate(report)
        assertFalse(res.isValid)
        assertTrue(res.errors.any { it.contains("side_effect_level must be one of allowed values") })
    }

    @Test
    fun testReportSerializationConstraints() {
        val catalog = IntentInvocationCatalog(catalog_schema_version = 1, candidate_count = 0, candidates = emptyList())
        val report = createMinimalReport(catalog)
        
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(IntentSurfaceReport::class.java)
        val jsonStr = adapter.toJson(report)
        
        assertTrue(jsonStr.contains("\"schema_id\": \"urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233\""))
        assertTrue(jsonStr.contains("\"schema_version\": 5"))
        assertTrue(jsonStr.contains("\"intent_invocation_catalog\""))
        assertFalse(jsonStr.contains("\"report_kind\""))
        assertFalse(jsonStr.contains("\"schema_semver\""))
        assertFalse(jsonStr.contains("\"schema_family_id\""))
        assertFalse(jsonStr.contains("\"catalog_kind\""))
    }

    @Test
    fun testReportKindIsAbsent() {
        val clazz = IntentSurfaceReport::class.java
        val fields = clazz.declaredFields.map { it.name }
        assertFalse("report_kind should not be present in IntentSurfaceReport", fields.contains("report_kind"))
    }

    @Test
    fun testSchemaSemverIsAbsent() {
        val clazz = IntentSurfaceReport::class.java
        val fields = clazz.declaredFields.map { it.name }
        assertFalse("schema_semver should not be present in IntentSurfaceReport", fields.contains("schema_semver"))
    }

    @Test
    fun testSchemaFamilyIdIsAbsent() {
        val clazz = IntentSurfaceReport::class.java
        val fields = clazz.declaredFields.map { it.name }
        assertFalse("schema_family_id should not be present in IntentSurfaceReport", fields.contains("schema_family_id"))
    }
}
