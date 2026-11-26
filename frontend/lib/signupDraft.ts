// 회원가입은 1단계(정보 입력)와 2단계(간편비밀번호 설정)를 거친 뒤 한 번에 요청한다.
// 비밀번호를 저장소에 남기지 않으려고 화면 이동 사이에는 메모리에만 둔다 (새로고침하면 1단계부터 다시).
export type SignupDraft = { name: string; email: string; phone: string; password: string };
let draft: SignupDraft | null = null;

export const saveSignupDraft = (d: SignupDraft) => { draft = d; };
export const loadSignupDraft = () => draft;
export const clearSignupDraft = () => { draft = null; };

// 가입 흐름(/signup, /pin-setup)을 벗어날 때마다 올라가는 번호. 가입 화면은 이 번호를 key 로 써서,
// 다시 들어오면 보관한 값 없이 빈 화면으로 새로 만든다 (Next.js 는 벗어난 화면의 상태를 보존한다)
let flow = 0;
const listeners = new Set<() => void>();
export const subscribeSignupFlow = (listener: () => void) => {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
};
export const getSignupFlow = () => flow;
export const resetSignupFlow = () => {
  draft = null;
  flow += 1;
  listeners.forEach((listener) => listener());
};

// 서버의 가입 규칙과 같다 (SignupRequest)
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#%^&*])[A-Za-z\d!@#%^&*]{8,15}$/;
export const PHONE_RULE = /^01\d{8,9}$/;
export const NAME_RULE = /^([가-힣]+|[A-Za-z]+( [A-Za-z]+)*)$/; // 한글만 또는 영문만. 앞뒤 공백을 지운 값에 적용한다
// 입력하는 순간 지우는 문자. 한글을 조합하는 중에 나오는 낱자(천지인의 점 포함)는 남겨 두고, 제출할 때 NAME_RULE 로 거른다
const NAME_BLOCKED = /[^가-힣ㄱ-ㅎㅏ-ㅣ\u318D\u119E\u11A2A-Za-z ]/g;
const HAS_HANGUL = /[가-힣ㄱ-ㅎㅏ-ㅣ]/;
export const isMixedName = (v: string) => HAS_HANGUL.test(v) && /[A-Za-z]/.test(v);
// 넣을 수 없는 문자를 지운 이름과, 지운 것이 있으면 그 이유(notice)를 돌려준다
export const cleanName = (v: string) => {
  const allowed = v.replace(NAME_BLOCKED, "");
  const spaced = allowed.replace(/ {2,}/g, " ").replace(/^ /, "");
  const name = HAS_HANGUL.test(spaced) ? spaced.replace(/ /g, "") : spaced; // 띄어쓰기는 영문 이름만
  const removed = (v.match(NAME_BLOCKED) ?? []).join("").replace(/\s/g, ""); // 탭 같은 다른 공백은 띄어쓰기로 안내한다
  const notice = /\p{N}/u.test(removed) ? "숫자는 입력할 수 없어요"
    : /[\p{L}\p{M}]/u.test(removed) ? "악센트 문자나 다른 언어의 문자는 입력할 수 없어요"
    : removed ? "기호는 입력할 수 없어요"
    : name !== v ? "띄어쓰기는 영문 이름의 단어 사이에 한 칸만 할 수 있어요"
    : "";
  return { name, notice };
};
