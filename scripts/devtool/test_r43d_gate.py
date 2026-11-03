import unittest
import r43d_gate

class R43DDestinationSourceContractTests(unittest.TestCase):
    def test_public_fixed_destination_is_presentational_only(self):
        data = r43d_gate.check()
        self.assertEqual(data['result'], 'R43D_DESTINATION_UX_SOURCE_PASS')
        self.assertFalse(data['production_update_enabled'])
        self.assertFalse(data['physical_device_qualified'])

    def test_debug_picker_remains_guarded(self):
        wizard = r43d_gate.read('app/src/main/java/com/sdvsync/ui/components/CinderboxWizardDialog.kt')
        vm = r43d_gate.read('app/src/main/java/com/sdvsync/ui/viewmodels/GameDownloadViewModel.kt')
        self.assertIn('if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(LocalContext.current)) {', wizard)
        self.assertIn('if (DeveloperFeaturePolicy.canChooseAlternativeDownloadPath(context) == false)', vm)

    def test_presentation_has_no_download_authorization(self):
        ui = r43d_gate.read('app/src/main/java/com/sdvsync/ui/policy/R43DDestinationPresentation.kt')
        for denied in ('File(', 'Files.', 'mkdir(', 'renameTo(', 'delete(', 'isWriteAllowed', 'canDownload'):
            self.assertNotIn(denied, ui)

if __name__ == '__main__': unittest.main()
