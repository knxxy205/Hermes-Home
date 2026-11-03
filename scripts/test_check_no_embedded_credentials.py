import unittest
from check_no_embedded_credentials import findings


class CredentialGuardTest(unittest.TestCase):
    def test_rejects_nonempty_preset_and_typed_default(self):
        self.assertEqual([1, 2], findings('password = "fixture-only"\nval hermesApiKey: String = "fixture-only"'))

    def test_rejects_embedded_server_environment(self):
        self.assertEqual([1], findings('"HERMES_API_KEY=fixture-only\\nSSH_PORT=22"'))

    def test_accepts_empty_configuration_and_explicit_placeholders(self):
        self.assertEqual([], findings('sshPassword = ""\nHERMES_API_KEY=<configure-on-server>'))

    def test_does_not_confuse_preference_identifiers_with_credentials(self):
        self.assertEqual([], findings('const val KEY_SSH_PASSWORD = "enc_ssh_password"'))

    def test_rejects_credentials_in_remembered_compose_state(self):
        self.assertEqual([1, 2], findings(
            'var sshPassword by remember { mutableStateOf("fixture-only") }\n'
            'val hermesKey = mutableStateOf<String>("fixture-only")'
        ))

    def test_accepts_empty_or_user_supplied_compose_state(self):
        self.assertEqual([], findings(
            'var sshPassword by remember { mutableStateOf("") }\n'
            'val hermesKey = mutableStateOf(config.hermesApiKey)'
        ))


if __name__ == '__main__':
    unittest.main()
