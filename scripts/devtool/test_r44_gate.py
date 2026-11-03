import unittest
import r44_gate

class R44SourceOnlyTests(unittest.TestCase):
    def test_foundation_is_source_only_and_fail_closed(self):
        result = r44_gate.check()
        self.assertEqual('R44_M3_FOUNDATION_SOURCE_PASS',result['result'])
        self.assertFalse(result['live_updates_enabled'])
        self.assertFalse(result['ui_device_qualified'])
        self.assertFalse(result['release_enabled'])
    def test_font_scale_guard_ignores_documentation_but_rejects_code(self):
        documentation = "/** no fontScale overrides */\n// TextUnit.Unspecified\nval size = 16.sp\n"
        self.assertNotIn('fontScale', r44_gate.kotlin_code_only(documentation))
        self.assertNotIn('TextUnit.Unspecified', r44_gate.kotlin_code_only(documentation))
        code = documentation + 'val fontScale = 0.8f\n'
        self.assertIn('fontScale', r44_gate.kotlin_code_only(code))
        self.assertIn('TextUnit.Unspecified', r44_gate.kotlin_code_only(code + 'val bad = TextUnit.Unspecified\n'))

    def test_classic_pixel_art_is_preserved(self):
        card = r44_gate.read('app/src/main/java/com/sdvsync/ui/components/StardewCard.kt')
        button = r44_gate.read('app/src/main/java/com/sdvsync/ui/components/StardewButton.kt')
        self.assertIn('.pixelBorder()', card)
        self.assertEqual(button.count('else modifier.drawBehind {'),2)
    def test_earthy_modern_tokens_are_opt_in(self):
        theme = r44_gate.read('app/src/main/java/com/sdvsync/ui/theme/Theme.kt')
        self.assertIn('R44ProductTypography else Typography',theme)
        self.assertIn('R44ProductShapes else StardewShapes',theme)
    def test_visual_status_is_not_write_authorization(self):
        tone = r44_gate.read('app/src/main/java/com/sdvsync/ui/policy/R44StatusTone.kt')
        status = r44_gate.read('app/src/main/java/com/sdvsync/ui/components/R44StatusSummary.kt')
        self.assertNotIn('File(',tone + status)
        self.assertNotIn('startDownload(',tone + status)
        self.assertIn('tone.label',status)
    def test_legacy_r43_status_copy_remains(self):
        screen = r44_gate.read('app/src/main/java/com/sdvsync/ui/screens/GameDownloadScreen.kt')
        self.assertIn('Existing installations are protected',screen)
        self.assertIn('R43DDestinationPresentation.from(',screen)

if __name__ == '__main__': unittest.main()
