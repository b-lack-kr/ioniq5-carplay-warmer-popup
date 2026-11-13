# CarPlay WARMER Popup · by blackr

CarPlay 사용 중 WARMER 버튼을 누르면 CarPlay 화면 위에서 열선과 통풍을 조절할 수 있는 아이오닉 5용 패키지입니다. 차량의 라이트·다크 테마에 맞춰 표시하며, WARMER 버튼을 다시 누르거나 다른 화면으로 이동하면 창이 닫힙니다.

**지원 대상:** 아이오닉 5 한국형 NEEV 260902 / Gen5W. NEMESIS와 HMG-API 1.0.2 이상이 설치되어 있고 CarPlay를 사용할 수 있어야 합니다. 다른 차종과 소프트웨어 버전의 호환성은 확인되지 않았습니다.

## 다운로드 및 설치

1. [릴리스](https://github.com/b-lack-kr/ioniq5-carplay-warmer-popup/releases/tag/v0.0.16)의 Assets에서 `CarPlay-WARMER-Popup-0.0.16-USB.zip`을 받습니다.
2. 압축파일의 내용을 FAT32 또는 exFAT USB의 최상위에 압축 해제합니다.
3. 차량에 USB를 연결하고 NEMESIS 확인 창에서 **Yes**를 누릅니다.
4. 설치가 끝나면 HMG-API에서 **CarPlay WARMER 화면 위 표시**를 켜고 **Apply changes**를 누른 뒤 내비게이션을 재부팅합니다.

올인원 패키지에 이미 포함되어 있다면 별도로 설치할 필요가 없습니다. 차량의 시트 옵션에 따라 표시되는 버튼이 달라질 수 있습니다. 0.0.16 단독 USB 설치본은 별도의 실차 설치 확인이 필요합니다.

삭제할 때는 HMG-API에서 관련 모드를 삭제하고 Apply changes를 적용한 뒤 NEMESIS의 My Packages에서 해당 패키지를 삭제합니다. NEMESIS와 HMG-API 자체는 유지하세요.

소스는 `warmer-source`, USB 설치 스크립트는 `usb-installer`에 있습니다. 릴리스 ZIP 안의 `SOURCE.zip`에도 대응 소스가 포함됩니다. 빌드 환경과 서명에 관한 내용은 [빌드 안내](warmer-source/BUILDING.md)를 확인하세요. HMG-API와 Android 시스템 구성요소는 각 프로젝트와 제공자의 조건을 따릅니다.
