# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "reportlab>=4.0.0",
# ]
# ///
"""Generate the synthetic purchase order used by the Vextis demo."""

from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "output" / "pdf" / "vextis-demo-purchase-order.pdf"


def money(value: float) -> str:
    return f"USD {value:,.2f}"


def build_purchase_order(output: Path = OUTPUT) -> Path:
    output.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    styles.add(
        ParagraphStyle(
            name="Right",
            parent=styles["BodyText"],
            alignment=TA_RIGHT,
            leading=15,
        )
    )
    styles.add(
        ParagraphStyle(
            name="Label",
            parent=styles["BodyText"],
            textColor=colors.HexColor("#5B5F75"),
            fontSize=8,
            leading=11,
            spaceAfter=2,
        )
    )

    document = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        rightMargin=20 * mm,
        leftMargin=20 * mm,
        topMargin=18 * mm,
        bottomMargin=18 * mm,
        title="Vextis Demo Purchase Order PO-2026-001",
        author="Vextis",
        subject="Synthetic hackathon demo data",
    )

    story = []
    header = Table(
        [
            [
                Paragraph(
                    "<b><font size='20' color='#5A46E8'>VEXTIS</font></b>",
                    styles["BodyText"],
                ),
                Paragraph(
                    "<b><font size='18'>PURCHASE ORDER</font></b>", styles["Right"]
                ),
            ],
            [
                Paragraph("Agentic enterprise operations", styles["Label"]),
                Paragraph("PO-2026-001", styles["Right"]),
            ],
        ],
        colWidths=[80 * mm, 80 * mm],
    )
    header.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))
    story.extend([header, Spacer(1, 8 * mm)])

    notice = Table(
        [
            [
                Paragraph(
                    "<b>SYNTHETIC DEMO DOCUMENT</b> - No real customer or personal data.",
                    styles["BodyText"],
                )
            ]
        ],
        colWidths=[160 * mm],
    )
    notice.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F0EDFF")),
                ("TEXTCOLOR", (0, 0), (-1, -1), colors.HexColor("#312783")),
                ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#A99CFF")),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ]
        )
    )
    story.extend([notice, Spacer(1, 8 * mm)])

    parties = Table(
        [
            [
                Paragraph("<b>SUPPLIER</b>", styles["Label"]),
                Paragraph("<b>CUSTOMER</b>", styles["Label"]),
            ],
            [
                Paragraph(
                    "Vextis Demo Supply<br/>Bogota, Colombia<br/>orders@vextis.example",
                    styles["BodyText"],
                ),
                Paragraph(
                    "Acme Colombia<br/>Customer ID: 11111111-1111-1111-1111-111111111111<br/>Bogota, Colombia",
                    styles["BodyText"],
                ),
            ],
        ],
        colWidths=[80 * mm, 80 * mm],
    )
    parties.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LINEBELOW", (0, 0), (-1, 0), 0.5, colors.HexColor("#D8D9E2")),
                ("TOPPADDING", (0, 1), (-1, 1), 7),
                ("BOTTOMPADDING", (0, 1), (-1, 1), 8),
            ]
        )
    )
    story.extend([parties, Spacer(1, 7 * mm)])

    metadata = Table(
        [
            ["Order date", "Requested delivery", "Payment terms", "Currency"],
            ["2026-08-24", "2026-09-05", "Net 30 days", "USD"],
        ],
        colWidths=[40 * mm] * 4,
    )
    metadata.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#17171A")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#D8D9E2")),
                ("TOPPADDING", (0, 0), (-1, -1), 7),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
            ]
        )
    )
    story.extend([metadata, Spacer(1, 8 * mm)])

    lines = [
        ("VXT-CHAIR-01", "Ergonomic task chair", 4, 285.00),
        ("VXT-DESK-01", "Adjustable standing desk", 2, 740.00),
    ]
    rows = [["SKU", "Description", "Quantity", "Unit price", "Line total"]]
    for sku, description, quantity, unit_price in lines:
        rows.append(
            [
                sku,
                description,
                str(quantity),
                money(unit_price),
                money(quantity * unit_price),
            ]
        )
    subtotal = sum(quantity * unit_price for _, _, quantity, unit_price in lines)
    rows.append(["", "", "", "TOTAL", money(subtotal)])

    items = Table(
        rows, colWidths=[34 * mm, 53 * mm, 20 * mm, 27 * mm, 29 * mm], repeatRows=1
    )
    items.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#5A46E8")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -2), 0.5, colors.HexColor("#D8D9E2")),
                ("ALIGN", (2, 1), (-1, -1), "RIGHT"),
                ("FONTNAME", (3, -1), (-1, -1), "Helvetica-Bold"),
                ("LINEABOVE", (3, -1), (-1, -1), 1, colors.HexColor("#5A46E8")),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
            ]
        )
    )
    story.extend([items, Spacer(1, 10 * mm)])

    story.append(
        Paragraph(
            "<b>Business instruction:</b> Validate the customer, confirm availability for every explicit SKU, "
            "verify Net 30 credit terms, and request human approval before committing inventory or issuing an invoice.",
            styles["BodyText"],
        )
    )
    story.extend(
        [
            Spacer(1, 13 * mm),
            Paragraph("Authorized for the Vextis hackathon demo", styles["Label"]),
        ]
    )

    document.build(story)
    return output


if __name__ == "__main__":
    print(build_purchase_order())
