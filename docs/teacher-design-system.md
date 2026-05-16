# Teacher App Design System

Design system nay duoc rut ra tu style Student App: olive green, nen am, card trang noi nhe, badge mem, spacing theo 8dp grid. Khong copy layout Student App; chi dung chung ngon ngu thiet ke.

## Tokens

| Token | Gia tri | Dung cho |
| --- | --- | --- |
| Primary | `#4B5320` | CTA chinh, app bar, icon active, tab selected |
| Primary dark | `#3A411A` | Pressed state |
| Primary soft | `#E9ECE0` | Button phu, logo container, active nav item |
| Accent | `#D97818` | Hanh dong phu, outline CTA, tao moi |
| Background | `#F5F6F0` | Nen man hinh |
| Surface | `#FCFCF9` / white | Card, input, toolbar phu |
| Text primary | `#1A1C16` | Title, noi dung chinh |
| Text secondary | `#5D6057` | Subtitle, metadata |
| Open | bg `#D7F5D8`, text `#2E6B2F` | Dang mo |
| Upcoming | bg `#FFE6CC`, text `#C06400` | Sap dien ra |
| Closed | bg `#FFDAD6`, text `#BA1A1A` | Da dong |
| Review | bg `#FFF2C2`, text `#8D6100` | Can cham |

## Component Rules

| Component | Android style/resource | Quy tac |
| --- | --- | --- |
| PrimaryButton | `@style/Teacher.PrimaryButton` | Cao 52dp, radius 12dp, nen primary, chu trang bold |
| SecondaryButton | `@style/Teacher.SecondaryButton` | Nen primary soft, chu primary, dung cho action phu |
| OutlineButton | `@style/Teacher.OutlineButton` | Vien accent, chu accent, dung cho xem ket qua/huy |
| InputField | `@style/Teacher.InputField` | Outlined, radius 12dp, surface background |
| Card | `@style/Teacher.Card` | Surface, radius 16dp, elevation 2dp, stroke rat nhe |
| Badge Open | `@style/Teacher.Badge.Open` | Trang thai dang mo |
| Badge Upcoming | `@style/Teacher.Badge.Upcoming` | Trang thai sap dien ra |
| Badge Closed | `@style/Teacher.Badge.Closed` | Trang thai da dong |
| Badge Review | `@style/Teacher.Badge.Review` | Bai can cham |
| Header | `@drawable/bg_teacher_header` | Nen primary, text/icon white |

## Screen Guidance

- Login: giu nen off-white, logo trong container primary soft, input outlined, CTA primary.
- Dashboard: header primary voi loi chao giao vien; card thong bao/quick action dung surface va stroke nhe; action phu dung secondary button.
- Class list: moi lop la `Teacher.Card`, badge so hoc sinh/bai can cham dung status review/upcoming.
- Class detail: app bar primary; tab Bài thi/Học sinh/Thống kê dung primary cho selected; list item dung card surface.
- Exam wizard: moi buoc trong card rieng, step indicator dang chip; CTA tiep tuc/luu dung `Teacher.PrimaryButton`.
- OMR scan: khu vuc anh/quét dat trong card surface; loading/progress dung primary; loi nhan dien dung error container.
- Result detail: diem tong trong card noi lon; dap an chi tiet trong list card nho; visual debug dung nen trung tinh de anh OMR de doc.
