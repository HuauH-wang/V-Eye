type Props = {
  interactive?: boolean;
};

const BASE = `${import.meta.env.BASE_URL}auth-collage`;

export type CollageTile = {
  src: string;
  alt: string;
  className: string;
  caption?: string;
};

export const AUTH_COLLAGE_TILES: CollageTile[] = [
  {
    src: `${BASE}/forest-trail.jpg`,
    alt: "阳光下的森林步道",
    className: "tile-a",
    caption: "户外观测",
  },
  {
    src: `${BASE}/leaf-macro.jpg`,
    alt: "带水珠的叶片特写",
    className: "tile-b",
    caption: "视觉识别",
  },
  {
    src: `${BASE}/alpine.jpg`,
    alt: "阿尔卑斯山脉远景",
    className: "tile-c",
  },
  {
    src: `${BASE}/wildflower.jpg`,
    alt: "草甸中的野花",
    className: "tile-d",
  },
  {
    src: `${BASE}/mist-forest.jpg`,
    alt: "晨雾中的森林",
    className: "tile-e",
  },
];

export default function AuthCollage({ interactive = true }: Props) {
  return (
    <div className={`auth-collage${interactive ? " is-interactive" : ""}`}>
      {AUTH_COLLAGE_TILES.map((tile) => (
        <figure key={tile.className} className={`auth-collage-tile ${tile.className}`}>
          <img src={tile.src} alt={tile.alt} loading="eager" decoding="async" />
          {tile.caption ? <figcaption>{tile.caption}</figcaption> : null}
        </figure>
      ))}
    </div>
  );
}
