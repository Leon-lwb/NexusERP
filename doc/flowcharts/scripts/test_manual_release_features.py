import unittest
from pathlib import Path

from docx import Document


class ReleaseManualTests(unittest.TestCase):
    def test_report_and_local_eda_operations_are_documented(self):
        source = Path(__file__).resolve().parents[3] / 'doc/system/user-manual/NexusERP_用户操作手册.docx'
        document = Document(source)
        text = '\n'.join(paragraph.text for paragraph in document.paragraphs)
        for required in (
            '自研报表', '创建基础报表', '受控查询', '我的报表交付',
            '14.14 工程设备与本地连接器', '14.15 创建与跟踪工程任务',
            '14.16 线路、三维与DRC复核', 'SSH', 'PARTIAL_SUCCESS',
        ):
            self.assertIn(required, text, required)
        self.assertNotIn('报表模块基于JimuReport', text)
        self.assertEqual(len(document.inline_shapes), 29)


if __name__ == '__main__':
    unittest.main()
