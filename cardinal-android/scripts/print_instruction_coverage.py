import sys
import xml.etree.ElementTree as ET


def main() -> None:
    report_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/reports/jacoco/jacocoDebugReport/jacocoDebugReport.xml"
    xml_root = ET.parse(report_path).getroot()
    instruction_counter = next(
        counter for counter in xml_root.findall("counter") if counter.attrib["type"] == "INSTRUCTION"
    )
    missed_count = int(instruction_counter.attrib["missed"])
    covered_count = int(instruction_counter.attrib["covered"])
    total_instructions = covered_count + missed_count
    coverage_pct = covered_count / total_instructions * 100 if total_instructions else 0
    coverage_pct_rounded = round(coverage_pct)
    print(f"Total {coverage_pct_rounded}%")


if __name__ == "__main__":
    main()
