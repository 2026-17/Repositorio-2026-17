function Footer() {
    return (
        <footer className="footer">
            <div className="container footer__inner">
                <div className="footer__top">
                    <div className="footer__logo">SIGAC</div>
                    <a href="mailto:acusab@gmail.com" className="footer__contact">
                        <span className="footer__contact-icon">✉</span>
                        acusab@gmail.com
                    </a>
                </div>
                <div className="footer__bottom">
                    <p className="footer__copy">
                        &copy; {new Date().getFullYear()} <strong>SIGAC.</strong> Asociación Cultural de Santa Bárbara (ACUSAB). Todos los derechos reservados.
                    </p>
                </div>
            </div>
        </footer>
    );
}

export default Footer;